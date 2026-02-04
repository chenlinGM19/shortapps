package com.shortapps.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView; // Added import

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shortapps.app.model.ShortcutItem;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverlayService extends Service {

    public static final String ACTION_TOGGLE_WINDOW = "com.shortapps.app.ACTION_TOGGLE_WINDOW";
    public static final String ACTION_SHOW_WINDOW = "com.shortapps.app.ACTION_SHOW_WINDOW";
    public static final String ACTION_HIDE_WINDOW = "com.shortapps.app.ACTION_HIDE_WINDOW";
    
    private WindowManager windowManager;
    
    // Map ConfigID -> TriggerView
    private Map<String, View> activeTriggers = new HashMap<>();
    private Map<String, WindowManager.LayoutParams> triggerParamsMap = new HashMap<>();
    
    // Map ConfigID -> Window Container View
    private Map<String, View> activeWindows = new HashMap<>();
    
    private List<WindowConfig> configs;
    private int screenWidth;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        
        configs = ConfigManager.loadWindows(this);
        
        startForeground(1001, createNotification());
        
        refreshTriggers();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_WINDOW);
        filter.addAction(ACTION_SHOW_WINDOW);
        filter.addAction(ACTION_HIDE_WINDOW);
        registerReceiver(internalReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    
    private final BroadcastReceiver internalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleIntent(intent);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            configs = ConfigManager.loadWindows(this);
            refreshTriggers();
            updateNotification();
            handleIntent(intent);
        }
        return START_STICKY;
    }
    
    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String windowName = intent.getStringExtra("window_name");
        
        if (windowName == null) return;
        
        WindowConfig target = null;
        for (WindowConfig c : configs) {
            if (c.getName().equalsIgnoreCase(windowName)) {
                target = c;
                break;
            }
        }
        
        if (target == null) return;
        
        if (ACTION_TOGGLE_WINDOW.equals(action)) {
            toggleWindow(target);
        } else if (ACTION_SHOW_WINDOW.equals(action)) {
            showWindow(target);
        } else if (ACTION_HIDE_WINDOW.equals(action)) {
            hideWindow(target);
        }
    }

    // --- Trigger Logic ---
    
    private void refreshTriggers() {
        for (String id : activeTriggers.keySet()) {
            if (activeTriggers.get(id) != null) {
                try {
                    windowManager.removeView(activeTriggers.get(id));
                } catch (Exception e) {}
            }
        }
        activeTriggers.clear();
        triggerParamsMap.clear();
        
        for (WindowConfig c : configs) {
            if (c.isTriggerEnabled()) {
                addTrigger(c);
            }
        }
    }
    
    private void addTrigger(WindowConfig config) {
        int widthPx = (int) (config.getTriggerWidth() * getResources().getDisplayMetrics().density);
        int heightPx = (int) (config.getTriggerHeight() * getResources().getDisplayMetrics().density);
        int radiusPx = (int) (config.getTriggerRadius() * getResources().getDisplayMetrics().density);
        
        View trigger = new View(this);
        
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radiusPx);
        
        int color = config.getTriggerColor();
        int style = config.getTriggerStyle();
        
        switch (style) {
            case 0: // Solid
                shape.setColor(color);
                break;
            case 1: // Outline
                shape.setColor(Color.TRANSPARENT);
                shape.setStroke(4, color);
                break;
            case 2: // Glass (Force alpha)
                shape.setColor(Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)));
                shape.setStroke(2, Color.WHITE);
                break;
            case 3: // Inverted
                shape.setColor(Color.WHITE);
                shape.setStroke(4, color);
                break;
            default:
                shape.setColor(color);
        }
        
        trigger.setBackground(shape);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                widthPx, heightPx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = config.getTriggerX();
        params.y = config.getTriggerY();
        
        trigger.setOnTouchListener(new TriggerTouchListener(config, params, trigger));
        
        windowManager.addView(trigger, params);
        activeTriggers.put(config.getId(), trigger);
        triggerParamsMap.put(config.getId(), params);
    }
    
    private class TriggerTouchListener implements View.OnTouchListener {
        private WindowConfig config;
        private WindowManager.LayoutParams params;
        private View view;
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private boolean isDrag = false;

        TriggerTouchListener(WindowConfig config, WindowManager.LayoutParams params, View view) {
            this.config = config;
            this.params = params;
            this.view = view;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDrag = false;
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - initialTouchX) > 20 || Math.abs(event.getRawY() - initialTouchY) > 20) {
                        isDrag = true;
                    }
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    try {
                        windowManager.updateViewLayout(view, params);
                    } catch (Exception e) {}
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    if (!isDrag) {
                        toggleWindow(config);
                    } else {
                        snapToEdge(params, view, config);
                    }
                    return true;
            }
            return false;
        }
    }
    
    private void snapToEdge(WindowManager.LayoutParams params, View view, WindowConfig config) {
        int mid = screenWidth / 2;
        int targetX = (params.x > mid) ? screenWidth - view.getWidth() : 0;
        
        ValueAnimator anim = ValueAnimator.ofInt(params.x, targetX);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator());
        anim.addUpdateListener(animation -> {
            params.x = (int) animation.getAnimatedValue();
            try {
                windowManager.updateViewLayout(view, params);
            } catch (Exception e) {}
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                config.setTriggerX(params.x);
                config.setTriggerY(params.y);
                saveConfigState(); 
            }
        });
        anim.start();
    }
    
    private void saveConfigState() {
        ConfigManager.saveWindows(this, configs);
    }
    
    // --- Window Logic ---
    
    private void toggleWindow(WindowConfig config) {
        if (activeWindows.containsKey(config.getId())) {
            hideWindow(config);
        } else {
            showWindow(config);
        }
    }
    
    private void showWindow(WindowConfig config) {
        if (activeWindows.containsKey(config.getId())) return;
        
        // Root Container (Catch Outside Touches)
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setOnClickListener(v -> hideWindow(config)); // Close on click outside
        
        // Actual Window Content
        FrameLayout contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundResource(R.drawable.bg_glass_panel);
        contentFrame.setPadding(20, 20, 20, 20);
        contentFrame.setClickable(true); // Prevent clicks passing through to root
        
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, config.getColumns()));
        rv.setAdapter(new ShortcutAdapter(config.getItems(), config));
        contentFrame.addView(rv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
            (int) (320 * getResources().getDisplayMetrics().density), 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        frameParams.gravity = Gravity.CENTER;
        rootContainer.addView(contentFrame, frameParams);
        
        // Window Manager Params (Match Parent to catch all touches)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                // Removed FLAG_NOT_FOCUSABLE so we catch touches. 
                // Added FLAG_LAYOUT_IN_SCREEN to cover everything.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
        );
        params.dimAmount = 0.4f;
        
        windowManager.addView(rootContainer, params);
        
        // Optimized Animation
        contentFrame.setAlpha(0f);
        contentFrame.setScaleX(0.9f);
        contentFrame.setScaleY(0.9f);
        
        contentFrame.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(200)
            .setInterpolator(new OvershootInterpolator(0.8f))
            .start();
        
        activeWindows.put(config.getId(), rootContainer);
    }
    
    private void hideWindow(WindowConfig config) {
        View root = activeWindows.remove(config.getId());
        if (root != null) {
            // Find content view to animate
            View content = ((ViewGroup)root).getChildAt(0);
            if (content != null) {
                content.animate()
                    .scaleX(0.9f).scaleY(0.9f).alpha(0f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        try {
                            windowManager.removeView(root);
                        } catch (Exception e) {}
                    }).start();
            } else {
                 try { windowManager.removeView(root); } catch (Exception e) {}
            }
        }
    }

    // --- Adapter ---
    private class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.Holder> {
        List<ShortcutItem> items;
        WindowConfig parentConfig;
        
        ShortcutAdapter(List<ShortcutItem> items, WindowConfig parentConfig) { 
            this.items = items; 
            this.parentConfig = parentConfig;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout v = new FrameLayout(parent.getContext());
            // Make height flexible based on content
            v.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            ShortcutItem item = items.get(position);
            holder.bind(item);
        }

        @Override public int getItemCount() { return items.size(); }
        
        class Holder extends RecyclerView.ViewHolder {
            FrameLayout root;
            FrameLayout contentWrapper;
            ImageView icon;
            View colorBlock;
            TextView label;
            
            Holder(View v) { 
                super(v); 
                root = (FrameLayout) v;
                root.setClickable(true);
                root.setFocusable(true);
                int pad = 8;
                root.setPadding(pad, pad, pad, pad);
                
                // Create a container that will hold the Icon/ColorBlock
                // This container is set to a fixed size (56dp) to simulate standard app icon size
                int iconSizePx = (int) (56 * OverlayService.this.getResources().getDisplayMetrics().density);
                contentWrapper = new FrameLayout(OverlayService.this);
                FrameLayout.LayoutParams wrapperParams = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
                wrapperParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                root.addView(contentWrapper, wrapperParams);

                icon = new ImageView(OverlayService.this);
                contentWrapper.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                
                colorBlock = new View(OverlayService.this);
                contentWrapper.addView(colorBlock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                
                label = new TextView(OverlayService.this);
                label.setTextSize(12);
                label.setTextColor(Color.WHITE);
                label.setGravity(Gravity.CENTER);
                label.setShadowLayer(2, 1, 1, Color.BLACK);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                
                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                labelParams.topMargin = iconSizePx + 4; // Position text below icon
                root.addView(label, labelParams);
            }
            
            void bind(ShortcutItem item) {
                root.setOnClickListener(v -> executeItem(item));
                label.setText(item.getLabel());
                
                if (item.getDisplayMode() == ShortcutItem.MODE_COLOR_BLOCK) {
                    icon.setVisibility(View.GONE);
                    colorBlock.setVisibility(View.VISIBLE);
                    
                    GradientDrawable bg = new GradientDrawable();
                    // App icons usually have roughly 20-25% radius relative to size, or can be square
                    // We'll use a moderate radius to look like an icon
                    bg.setCornerRadius(30); 
                    bg.setColor(item.getColorInfo());
                    colorBlock.setBackground(bg);
                } else {
                    colorBlock.setVisibility(View.GONE);
                    icon.setVisibility(View.VISIBLE);
                    try {
                        if (item.getType() == ShortcutItem.TYPE_APP) {
                            Drawable d = getPackageManager().getApplicationIcon(item.getPackageName());
                            icon.setImageDrawable(d);
                        } else {
                            icon.setImageResource(android.R.drawable.ic_menu_compass);
                            icon.setColorFilter(Color.WHITE);
                        }
                    } catch (Exception e) {
                        icon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                }
            }
        }
    }
    
    private void executeItem(ShortcutItem item) {
        try {
            if (item.getType() == ShortcutItem.TYPE_APP) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(item.getPackageName());
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }
            } else if (item.getType() == ShortcutItem.TYPE_TASKER) {
                Intent b = new Intent("net.dinglisch.android.tasker.ACTION_TASK");
                b.putExtra("task_name", item.getTaskerTaskName());
                sendBroadcast(b);
            } else if (item.getType() == ShortcutItem.TYPE_SHORTCUT) {
                 Intent i = Intent.parseUri(item.getIntentUri(), 0);
                 i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 startActivity(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Auto Close window after launch
        for (WindowConfig c : configs) {
            if (activeWindows.containsKey(c.getId())) {
                hideWindow(c);
            }
        }
    }

    private Notification createNotification() {
        String chId = "overlay_service";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(chId, "Shortapps Overlay", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_control);
        rv.removeAllViews(R.id.notif_container);
        
        for (WindowConfig c : configs) {
            if (c.isEnabledInNotification()) {
                RemoteViews btn = new RemoteViews(getPackageName(), R.layout.item_notif_button);
                btn.setTextViewText(R.id.btnWindow, c.getName());
                
                Intent i = new Intent(this, TaskerReceiver.class);
                i.setAction(ACTION_TOGGLE_WINDOW);
                i.putExtra("window_name", c.getName());
                
                // CRITICAL: Use unique RequestCode (hashCode) to ensure PendingIntents don't overwrite each other
                PendingIntent pi = PendingIntent.getBroadcast(
                    this, 
                    c.getName().hashCode(), 
                    i, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                
                btn.setOnClickPendingIntent(R.id.btnWindow, pi);
                rv.addView(R.id.notif_container, btn);
            }
        }
        
        // Main intent to open app
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPi = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, chId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCustomContentView(rv)
                .setContentIntent(mainPi)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    
    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1001, createNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (View v : activeTriggers.values()) windowManager.removeView(v);
        for (View v : activeWindows.values()) windowManager.removeView(v);
        unregisterReceiver(internalReceiver);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}