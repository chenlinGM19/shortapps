package com.timecurrency.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        String action = intent.getAction();
        if ("INCREMENT".equals(action)) {
            CurrencyManager.updateCurrency(context, 1);
        } else if ("DECREMENT".equals(action)) {
            CurrencyManager.updateCurrency(context, -1);
        }
    }
}