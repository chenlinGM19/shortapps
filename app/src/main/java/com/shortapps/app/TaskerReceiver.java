package com.shortapps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TaskerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Tasker sends a broadcast to open/toggle specific window
        if ("com.shortapps.ACTION_TOGGLE_WINDOW".equals(intent.getAction())) {
            // Re-trigger the service with a command, or if service is running, it should handle UI
            // Ideally, we bind to service or send a command intent to service
            // For simplicity, we just ensure service is running, but real toggling needs service reference or bus
            
            // In a simple architecture without EventBus, we can restart service with extra
            Intent serviceIntent = new Intent(context, OverlayService.class);
            // Not implemented fully in Service for specific window toggle via intent yet, 
            // but this is the entry point.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}