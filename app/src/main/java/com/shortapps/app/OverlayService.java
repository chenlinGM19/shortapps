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
import android.content.res.Configuration;
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
import android.widget.TextView;

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
    private Map<String, View> activeTriggers = new HashMap<>();
    private Map<String, WindowManager.LayoutParams> triggerParamsMap = new HashMap<>();
    private Map<String, View> activeWindows = new HashMap<>();
    private List<WindowConfig> configs;
    private int screenWidth;
    private int screenHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        updateScreenDimensions();
        configs = ConfigManager.loadWindows(this);
        startForeground(1001, createNotification());
        refreshTriggers();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_WINDOW);
        filter.addAction(ACTION_SHOW_WINDOW);
        filter.addAction(ACTION_HIDE_WINDOW);
        registerReceiver(internalReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    
    private void updateScreenDimensions() {
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int oldWidth = screenWidth;
        updateScreenDimensions();
        
        // Reposition triggers based on edge snapping logic
        for (String id : triggerParamsMap.keySet()) {
            WindowManager.LayoutParams params = triggerParamsMap.get(id);
            View view = activeTriggers.get(id);
            
            if (params != null && view != null) {
                // Determine snap logic based on config
                WindowConfig c = null;
                for(WindowConfig wc : configs) { if(wc.getId().equals(id)) { c=wc; break; } }
                
                if (c != null && c.isCornerSnap()) {
                     int anchor = c.getCornerAnchor();
                     // 0: TL, 1: TR, 2: BL, 3: BR
                     if (anchor == -1) {
                         // Fallback if anchor never set: calculate nearest based on old rel pos
                         // but simpler to just default to nearest now
                         snapToClosestCorner(params, view, c, false); // Snap immediately without anim update config only
                     } else {
                         // Force position based on anchor and NEW screen dimensions
                         switch (anchor) {
                             case 0: // TL
                                 params.x = 0; 
                                 params.y = 0; 
                                 break;
                             case 1: // TR
                                 params.x = screenWidth - view.getWidth(); 
                                 params.y = 0; 
                                 break;
                             case 2: // BL
                                 params.x = 0; 
                                 params.y = screenHeight - view.getHeight(); 
                                 break;
                             case 3: // BR
                                 params.x = screenWidth - view.getWidth(); 
                                 params.y = screenHeight - view.getHeight(); 
                                 break;
                         }
                     }
                } else {
                    // Vertical Edge Logic
                    if (params.x > oldWidth / 2) {
                         params.x = screenWidth - view.getWidth();
                    } else {
                         params.x = 0;
                    }
                    
                    // Clamp Y
                    if (params.y > screenHeight - view.getHeight()) {
                        params.y = screenHeight - view.getHeight();
                    }
                }
                
                try {
                    windowManager.updateViewLayout(view, params);
                    if (c != null) {
                        c.setTriggerX(params.x);
                        c.setTriggerY(params.y);
                    }
                } catch (Exception e) {}
            }
        }
        ConfigManager.saveWindows(this, configs);
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
        float d = getResources().getDisplayMetrics().density;
        int widthPx = (int) (config.getTriggerWidth() * d);
        int heightPx = (int) (config.getTriggerHeight() * d);
        
        View trigger = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        
        // 4 radii: TL, TR, BR, BL
        float[] radii = new float[] {
            config.getRadiusTL() * d, config.getRadiusTL() * d,
            config.getRadiusTR() * d, config.getRadiusTR() * d,
            config.getRadiusBR() * d, config.getRadiusBR() * d,
            config.getRadiusBL() * d, config.getRadiusBL() * d
        };
        shape.setCornerRadii(radii);
        
        int color = config.getTriggerColor();
        int style = config.getTriggerStyle();
        
        switch (style) {
            case 0: shape.setColor(color); break; // Solid
            case 1: // Outline
                shape.setColor(Color.TRANSPARENT);
                shape.setStroke(4, color);
                break;
            case 2: // Glass
                shape.setColor(Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)));
                shape.setStroke(2, Color.WHITE);
                break;
            case 3: // Inverted
                shape.setColor(Color.WHITE);
                shape.setStroke(4, color);
                break;
            default: shape.setColor(color);
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
                    try { windowManager.updateViewLayout(view, params); } catch (Exception e) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    if (!isDrag) toggleWindow(config);
                    else snapToDestination(params, view, config);
                    return true;
            }
            return false;
        }
    }
    
    private void snapToDestination(WindowManager.LayoutParams params, View view, WindowConfig config) {
        if (config.isCornerSnap()) {
            snapToClosestCorner(params, view, config, true);
        } else {
            snapToVerticalEdge(params, view, config);
        }
    }

    private void snapToClosestCorner(WindowManager.LayoutParams params, View view, WindowConfig config, boolean animate) {
        int w = view.getWidth();
        int h = view.getHeight();
        int currentX = params.x;
        int currentY = params.y;

        // Define Corners
        // 0: TL (0,0)
        // 1: TR (screenWidth-w, 0)
        // 2: BL (0, screenHeight-h)
        // 3: BR (screenWidth-w, screenHeight-h)

        int[] xTargets = {0, screenWidth - w, 0, screenWidth - w};
        int[] yTargets = {0, 0, screenHeight - h, screenHeight - h};
        
        int bestIdx = 0;
        double minDesc = Double.MAX_VALUE;
        
        for (int i=0; i<4; i++) {
            double dist = Math.pow(currentX - xTargets[i], 2) + Math.pow(currentY - yTargets[i], 2);
            if (dist < minDesc) {
                minDesc = dist;
                bestIdx = i;
            }
        }
        
        // Save the anchor for rotation handling
        config.setCornerAnchor(bestIdx);
        
        int targetX = xTargets[bestIdx];
        int targetY = yTargets[bestIdx];
        
        if (animate) {
            animateMove(params, view, config, targetX, targetY);
        } else {
            params.x = targetX;
            params.y = targetY;
            try { windowManager.updateViewLayout(view, params); } catch (Exception e) {}
            config.setTriggerX(targetX);
            config.setTriggerY(targetY);
            // Anchor already set above
        }
    }
    
    private void snapToVerticalEdge(WindowManager.LayoutParams params, View view, WindowConfig config) {
        int mid = screenWidth / 2;
        int targetX = (params.x > mid) ? screenWidth - view.getWidth() : 0;
        int targetY = params.y; // Keep Y as is
        
        // Clamp Y
        if (targetY < 0) targetY = 0;
        if (targetY > screenHeight - view.getHeight()) targetY = screenHeight - view.getHeight();
        
        animateMove(params, view, config, targetX, targetY);
    }
    
    private void animateMove(WindowManager.LayoutParams params, View view, WindowConfig config, int targetX, int targetY) {
        ValueAnimator animX = ValueAnimator.ofInt(params.x, targetX);
        ValueAnimator animY = ValueAnimator.ofInt(params.y, targetY);
        
        animX.addUpdateListener(animation -> {
            params.x = (int) animation.getAnimatedValue();
            // We update in one listener, but need latest Y from other animator? 
            // Better to use a single animator update if possible or just update layout in both
            try { windowManager.updateViewLayout(view, params); } catch (Exception e) {}
        });
        
        animY.addUpdateListener(animation -> {
             params.y = (int) animation.getAnimatedValue();
             try { windowManager.updateViewLayout(view, params); } catch (Exception e) {}
        });
        
        AnimatorListenerAdapter endListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                config.setTriggerX(params.x);
                config.setTriggerY(params.y);
                saveConfigState(); 
            }
        };
        
        // Use an AnimatorSet to run them together
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(animX, animY);
        set.setDuration(300);
        set.setInterpolator(new OvershootInterpolator());
        set.addListener(endListener);
        set.start();
    }
    
    private void saveConfigState() {
        ConfigManager.saveWindows(this, configs);
    }
    
    private void toggleWindow(WindowConfig config) {
        if (activeWindows.containsKey(config.getId())) hideWindow(config);
        else showWindow(config);
    }
    
    private void showWindow(WindowConfig config) {
        if (activeWindows.containsKey(config.getId())) return;
        
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setOnClickListener(v -> hideWindow(config));
        
        FrameLayout contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundResource(R.drawable.bg_glass_panel);
        contentFrame.setPadding(20, 20, 20, 20);
        contentFrame.setClickable(true);
        
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
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
        );
        params.dimAmount = 0.4f;
        
        windowManager.addView(rootContainer, params);
        contentFrame.setAlpha(0f);
        contentFrame.setScaleX(0.9f);
        contentFrame.setScaleY(0.9f);
        contentFrame.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(new OvershootInterpolator(0.8f)).start();
        activeWindows.put(config.getId(), rootContainer);
    }
    
    private void hideWindow(WindowConfig config) {
        View root = activeWindows.remove(config.getId());
        if (root != null) {
            View content = ((ViewGroup)root).getChildAt(0);
            if (content != null) {
                content.animate().scaleX(0.9f).scaleY(0.9f).alpha(0f).setDuration(150).withEndAction(() -> {
                    try { windowManager.removeView(root); } catch (Exception e) {}
                }).start();
            } else {
                 try { windowManager.removeView(root); } catch (Exception e) {}
            }
        }
    }

    // Custom Square Layout for 1:1 Aspect Ratio
    private static class SquareFrameLayout extends FrameLayout {
        public SquareFrameLayout(Context context) { super(context); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec); // Force Height = Width
        }
    }

    private class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.Holder> {
        List<ShortcutItem> items;
        WindowConfig parentConfig;
        
        ShortcutAdapter(List<ShortcutItem> items, WindowConfig parentConfig) { 
            this.items = items; 
            this.parentConfig = parentConfig;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout v = new FrameLayout(parent.getContext());
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
            SquareFrameLayout contentWrapper; // Use Square layout
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
                
                contentWrapper = new SquareFrameLayout(OverlayService.this);
                FrameLayout.LayoutParams wrapperParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
                
                root.removeView(contentWrapper); 
                
                android.widget.LinearLayout linear = new android.widget.LinearLayout(OverlayService.this);
                linear.setOrientation(android.widget.LinearLayout.VERTICAL);
                linear.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                root.addView(linear);
                
                linear.addView(contentWrapper);
                
                android.widget.LinearLayout.LayoutParams lpLabel = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpLabel.topMargin = 4;
                linear.addView(label, lpLabel);
            }
            
            void bind(ShortcutItem item) {
                root.setOnClickListener(v -> executeItem(item));
                
                if (parentConfig.isShowLabels()) {
                    label.setVisibility(View.VISIBLE);
                    label.setText(item.getLabel());
                } else {
                    label.setVisibility(View.GONE);
                }
                
                if (item.getDisplayMode() == ShortcutItem.MODE_COLOR_BLOCK) {
                    icon.setVisibility(View.GONE);
                    colorBlock.setVisibility(View.VISIBLE);
                    
                    GradientDrawable bg = new GradientDrawable();
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
        for (WindowConfig c : configs) {
            if (activeWindows.containsKey(c.getId())) hideWindow(c);
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