package com.shortapps.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View pillView;
    private View gridView;
    private WindowManager.LayoutParams pillParams;
    private List<DataModel.WindowConfig> windows;
    private int currentWindowIndex = 0;
    private boolean isGridOpen = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        
        windows = DataManager.loadWindows(this);
        
        if (pillView == null) {
            setupPill();
        } else {
            // Update pill size if changed
            updatePillSize();
        }
        
        // Update notification content
        updateNotification();

        return START_STICKY;
    }

    private void setupPill() {
        pillView = LayoutInflater.from(this).inflate(R.layout.overlay_pill, null);
        
        int sizeDp = DataManager.getPillSize(this);
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);

        pillParams = new WindowManager.LayoutParams(
                sizePx, sizePx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        pillParams.gravity = Gravity.TOP | Gravity.START;
        pillParams.x = 0;
        pillParams.y = 100;

        pillView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = pillParams.x;
                        initialY = pillParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        pillParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        pillParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(pillView, pillParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            toggleGrid(); // Click
                        } else {
                            snapToEdge(); // Drag release
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(pillView, pillParams);
    }
    
    private void updatePillSize() {
        int sizeDp = DataManager.getPillSize(this);
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);
        pillParams.width = sizePx;
        pillParams.height = sizePx;
        windowManager.updateViewLayout(pillView, pillParams);
    }

    private void snapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int halfWidth = screenWidth / 2;
        if (pillParams.x + pillParams.width / 2 < halfWidth) {
            pillParams.x = 0; // Left
        } else {
            pillParams.x = screenWidth - pillParams.width; // Right
        }
        windowManager.updateViewLayout(pillView, pillParams);
    }

    private void toggleGrid() {
        if (isGridOpen) {
            closeGrid();
        } else {
            if (!windows.isEmpty()) {
                openGrid(windows.get(currentWindowIndex));
            }
        }
    }

    private void closeGrid() {
        if (gridView != null) {
            try {
                windowManager.removeView(gridView);
            } catch (Exception e) {}
            gridView = null;
        }
        isGridOpen = false;
        // Animate pill back to normal?
    }

    public void openGrid(DataModel.WindowConfig window) {
        if (isGridOpen) closeGrid();
        
        gridView = LayoutInflater.from(this).inflate(R.layout.overlay_grid, null);
        GridLayout gridContainer = gridView.findViewById(R.id.gridContainer);
        TextView tvTitle = gridView.findViewById(R.id.tvWindowTitle);
        tvTitle.setText(window.name);
        
        gridContainer.setColumnCount(window.columns);

        for (DataModel.ShortcutItem item : window.items) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_grid_icon, gridContainer, false);
            ImageView iconView = itemView.findViewById(R.id.imgIcon);
            View blockView = itemView.findViewById(R.id.viewBlock);
            
            if (item.displayMode == DataModel.ShortcutItem.DisplayMode.ICON && item.type == DataModel.ShortcutItem.Type.APP) {
                // Show Icon
                try {
                    iconView.setImageDrawable(getPackageManager().getApplicationIcon(item.packageName));
                    iconView.setVisibility(View.VISIBLE);
                    blockView.setVisibility(View.GONE);
                } catch (PackageManager.NameNotFoundException e) {
                   // Fallback
                   iconView.setVisibility(View.GONE);
                   blockView.setVisibility(View.VISIBLE);
                }
            } else {
                // Show Block
                iconView.setVisibility(View.GONE);
                blockView.setVisibility(View.VISIBLE);
                GradientDrawable bg = (GradientDrawable) blockView.getBackground();
                try {
                    bg.setColor(Color.parseColor(item.blockColor));
                } catch (Exception e) {
                    bg.setColor(Color.DKGRAY);
                }
            }
            
            itemView.setOnClickListener(v -> executeShortcut(item));
            
            // Set params for grid
            GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
            gl.width = dpToPx(60);
            gl.height = dpToPx(60);
            gl.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            gridContainer.addView(itemView, gl);
        }

        // Close button
        gridView.findViewById(R.id.btnClose).setOnClickListener(v -> closeGrid());
        
        // Cycle windows button (if multiple)
        View btnNext = gridView.findViewById(R.id.btnNextWindow);
        if (windows.size() > 1) {
            btnNext.setVisibility(View.VISIBLE);
            btnNext.setOnClickListener(v -> {
                currentWindowIndex = (currentWindowIndex + 1) % windows.size();
                openGrid(windows.get(currentWindowIndex));
            });
        } else {
            btnNext.setVisibility(View.GONE);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        
        params.dimAmount = 0.5f;
        params.gravity = Gravity.CENTER;
        
        windowManager.addView(gridView, params);
        isGridOpen = true;
    }
    
    private void executeShortcut(DataModel.ShortcutItem item) {
        if (item.type == DataModel.ShortcutItem.Type.APP) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                closeGrid();
            }
        } else if (item.type == DataModel.ShortcutItem.Type.TASKER) {
            // Tasker Intent Protocol
            Intent intent = new Intent("net.dinglisch.android.tasker.ACTION_TASK");
            intent.putExtra("task_name", item.taskName);
            sendBroadcast(intent);
            closeGrid();
            
            // Fallback visualization
            Intent intentOld = new Intent("net.dinglisch.android.task.ACTION_TASK"); // Legacy
            intentOld.putExtra("task_name", item.taskName);
            sendBroadcast(intentOld);
        }
    }
    
    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, createNotification());
    }

    private Notification createNotification() {
        // Notification Layout: Show items of the FIRST enabled window for notification
        DataModel.WindowConfig notifWindow = null;
        for(DataModel.WindowConfig w : windows) {
            if(w.showInNotification) {
                notifWindow = w;
                break;
            }
        }
        
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_bar);
        
        if (notifWindow != null) {
            rv.setViewVisibility(R.id.llNotifItems, View.VISIBLE);
            rv.setTextViewText(R.id.tvNotifTitle, notifWindow.name);
            
            // Clear previous
            rv.removeAllViews(R.id.llNotifContainer);
            
            // Add max 5 items due to RemoteViews limits
            int limit = Math.min(notifWindow.items.size(), 5);
            for(int i=0; i<limit; i++) {
                DataModel.ShortcutItem item = notifWindow.items.get(i);
                RemoteViews itemRv = new RemoteViews(getPackageName(), R.layout.item_notification_icon);
                
                if (item.displayMode == DataModel.ShortcutItem.DisplayMode.BLOCK) {
                     itemRv.setViewVisibility(R.id.imgNotifIcon, View.GONE);
                     itemRv.setViewVisibility(R.id.viewNotifBlock, View.VISIBLE);
                     try {
                         itemRv.setInt(R.id.viewNotifBlock, "setColorFilter", Color.parseColor(item.blockColor));
                     } catch(Exception e){}
                } else {
                     // Loading Bitmap for RemoteViews is heavy, simplified logic:
                     // We can't easily put dynamic Package Icons in RemoteViews without saving them to file first
                     // fallback to block for notification or simple standard icon
                     itemRv.setViewVisibility(R.id.imgNotifIcon, View.VISIBLE);
                     itemRv.setViewVisibility(R.id.viewNotifBlock, View.GONE);
                }
                
                Intent action = new Intent(this, NotificationActionReceiver.class);
                action.setAction("EXECUTE_ITEM");
                action.putExtra("item_id", item.id);
                PendingIntent pi = PendingIntent.getBroadcast(this, i, action, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                itemRv.setOnClickPendingIntent(R.id.rootNotifItem, pi);
                
                rv.addView(R.id.llNotifContainer, itemRv);
            }
        } else {
            rv.setViewVisibility(R.id.llNotifItems, View.GONE);
            rv.setTextViewText(R.id.tvNotifTitle, "Shortapps Running");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "ShortappsChannel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setCustomContentView(rv)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("ShortappsChannel", "Shortapps Overlay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}