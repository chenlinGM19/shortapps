package com.shortapps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class TaskerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        // Relay to Service
        Intent serviceIntent = new Intent(context, OverlayService.class);
        serviceIntent.setAction(action);
        
        if (intent.hasExtra("window_name")) {
            serviceIntent.putExtra("window_name", intent.getStringExtra("window_name"));
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}