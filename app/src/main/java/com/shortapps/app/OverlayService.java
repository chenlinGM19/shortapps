package com.shortapps.app;

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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shortapps.app.model.ShortcutItem;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverlayService extends Service {

    public static final String ACTION_TOGGLE_WINDOW = "com.shortapps.app.ACTION_TOGGLE_WINDOW";
    public static final String ACTION_SHOW_WINDOW = "com.shortapps.app.ACTION_SHOW_WINDOW";
    public static final String ACTION_HIDE_WINDOW = "com.shortapps.app.ACTION_HIDE_WINDOW";
    
    private WindowManager windowManager;
    private View triggerButton;
    private WindowManager.LayoutParams triggerParams;
    
    // Map Window ID -> View
    private Map<String, View> activeWindows = new HashMap<>();
    private List<WindowConfig> configs;
    
    private int screenWidth;
    private int screenHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        configs = ConfigManager.loadWindows(this);
        
        startForeground(1001, createNotification());
        
        setupTriggerButton();
        
        // Register receiver for internal actions
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

    // --- Trigger Button Logic ---
    private void setupTriggerButton() {
        int size = ConfigManager.loadTriggerSize(this);
        int sizePx = (int) (size * getResources().getDisplayMetrics().density);
        
        triggerButton = new View(this);
        triggerButton.setBackgroundResource(R.drawable.bg_trigger_button);
        
        triggerParams = new WindowManager.LayoutParams(
                sizePx, sizePx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        triggerParams.gravity = Gravity.TOP | Gravity.START;
        triggerParams.x = screenWidth; // Start right
        triggerParams.y = screenHeight / 2;
        
        triggerButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = triggerParams.x;
                        initialY = triggerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDrag = false;
                        triggerButton.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > 20 || Math.abs(event.getRawY() - initialTouchY) > 20) {
                            isDrag = true;
                        }
                        triggerParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        triggerParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(triggerButton, triggerParams);
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        triggerButton.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        if (!isDrag) {
                            // Clicked: Open first window or specific menu?
                            // For MVP, toggle the first configured window
                            if (!configs.isEmpty()) toggleWindow(configs.get(0));
                        } else {
                            snapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
        
        windowManager.addView(triggerButton, triggerParams);
    }
    
    private void snapToEdge() {
        int mid = screenWidth / 2;
        int targetX = (triggerParams.x > mid) ? screenWidth - triggerButton.getWidth() : 0;
        
        ValueAnimator anim = ValueAnimator.ofInt(triggerParams.x, targetX);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator());
        anim.addUpdateListener(animation -> {
            triggerParams.x = (int) animation.getAnimatedValue();
            try {
                windowManager.updateViewLayout(triggerButton, triggerParams);
            } catch (Exception e) {}
        });
        anim.start();
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
        
        // Container
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundResource(R.drawable.bg_glass_panel);
        container.setPadding(20, 20, 20, 20);
        
        // RecyclerView
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, config.getColumns()));
        rv.setAdapter(new ShortcutAdapter(config.getItems()));
        container.addView(rv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (300 * getResources().getDisplayMetrics().density), // Fixed width or dynamic
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
        );
        params.dimAmount = 0.3f; // Glass feel
        params.gravity = Gravity.CENTER;
        
        // Allow close on outside touch? Hard with FLAG_NOT_FOCUSABLE. 
        // We implement a close button inside or rely on toggle.
        
        windowManager.addView(container, params);
        
        // Animate In
        container.setScaleX(0.5f);
        container.setScaleY(0.5f);
        container.setAlpha(0f);
        container.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(new OvershootInterpolator()).start();
        
        activeWindows.put(config.getId(), container);
    }
    
    private void hideWindow(WindowConfig config) {
        View v = activeWindows.remove(config.getId());
        if (v != null) {
            v.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).setDuration(200).withEndAction(() -> {
                try {
                    windowManager.removeView(v);
                } catch (Exception e) {}
            }).start();
        }
    }

    // --- Adapter ---
    private class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.Holder> {
        List<ShortcutItem> items;
        
        ShortcutAdapter(List<ShortcutItem> items) { this.items = items; }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout v = new FrameLayout(parent.getContext());
            int size = (int) (50 * getResources().getDisplayMetrics().density); // Use config size later
            v.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150));
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            ShortcutItem item = items.get(position);
            holder.bind(item);
        }

        @Override public int getItemCount() { return items.size(); }
        
        class Holder extends RecyclerView.ViewHolder {
            FrameLayout root;
            ImageView icon;
            View colorBlock;
            
            Holder(View v) { 
                super(v); 
                root = (FrameLayout) v;
                int pad = 10;
                root.setPadding(pad, pad, pad, pad);
                
                icon = new ImageView(OverlayService.this);
                root.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                
                colorBlock = new View(OverlayService.this);
                colorBlock.setBackgroundResource(R.drawable.bg_glass_panel); // Reuse shape
                root.addView(colorBlock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            
            void bind(ShortcutItem item) {
                root.setOnClickListener(v -> executeItem(item));
                
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
                            icon.setImageResource(android.R.drawable.ic_menu_agenda); // Placeholder for Tasker
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
        if (item.getType() == ShortcutItem.TYPE_APP) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(item.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
        } else if (item.getType() == ShortcutItem.TYPE_TASKER) {
            // Notify Tasker via Broadcast
            Intent b = new Intent("net.dinglisch.android.tasker.ACTION_TASK");
            b.putExtra("task_name", item.getTaskerTaskName());
            sendBroadcast(b);
            
            // Also notify ourselves (optional feedback)
            Intent i = new Intent(ACTION_HIDE_WINDOW); // Close window on action?
            // sendBroadcast(i); 
        }
    }

    private Notification createNotification() {
        String chId = "overlay_service";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(chId, "Overlay", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_control);
        // Dynamically add buttons for each window config
        // Simplification for XML output: just one generic view
        
        return new NotificationCompat.Builder(this, chId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCustomContentView(rv)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (triggerButton != null) windowManager.removeView(triggerButton);
        for (View v : activeWindows.values()) windowManager.removeView(v);
        unregisterReceiver(internalReceiver);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}