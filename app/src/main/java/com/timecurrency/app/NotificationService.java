package com.timecurrency.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class NotificationService extends Service {

    public static final String ACTION_REFRESH = "ACTION_REFRESH";
    private static final String CHANNEL_ID = "TimeCurrencyChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            startForeground(NOTIFICATION_ID, buildNotification(this), type);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    public static void refreshNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, buildNotification(context));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Notification buildNotification(Context context) {
        int amount = CurrencyManager.getCurrency(context);
        
        // 1. Get the correct icon resource based on the current active alias
        int iconResId = AppIconHelper.getCurrentIconResource(context);

        // 2. Use getLaunchIntentForPackage to ensure we open the currently ENABLED alias/activity
        // This fixes the issue where opening the app fails if MainActivity is disabled by an alias
        Intent openAppIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (openAppIntent == null) {
            // Fallback just in case, though getLaunchIntentForPackage is robust
            openAppIntent = new Intent(context, MainActivity.class);
        }
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent incIntent = new Intent(context, NotificationActionReceiver.class);
        incIntent.setAction("INCREMENT");
        PendingIntent pendingInc = PendingIntent.getBroadcast(
                context, 1, incIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent decIntent = new Intent(context, NotificationActionReceiver.class);
        decIntent.setAction("DECREMENT");
        PendingIntent pendingDec = PendingIntent.getBroadcast(
                context, 2, decIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.notification_custom);
        
        customView.setTextViewText(R.id.notif_amount, String.valueOf(amount));
        
        customView.setImageViewResource(R.id.notif_btn_plus, R.drawable.ic_plus);
        customView.setImageViewResource(R.id.notif_btn_minus, R.drawable.ic_minus);
        
        int colorPrimary = Color.parseColor("#D0BCFF"); 
        int colorError = Color.parseColor("#F2B8B5");
        
        customView.setInt(R.id.notif_btn_plus, "setColorFilter", colorPrimary);
        customView.setInt(R.id.notif_btn_minus, "setColorFilter", colorError);

        customView.setOnClickPendingIntent(R.id.notif_btn_plus, pendingInc);
        customView.setOnClickPendingIntent(R.id.notif_btn_minus, pendingDec);
        customView.setOnClickPendingIntent(R.id.notif_text_container, pendingOpenApp);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconResId) // Set the dynamic icon here
                .setCustomContentView(customView)
                .setCustomBigContentView(customView) 
                .setContentIntent(pendingOpenApp)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        
        createNotificationChannel(context);

        return builder.build();
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Currency Status",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Shows persistent time currency");
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}