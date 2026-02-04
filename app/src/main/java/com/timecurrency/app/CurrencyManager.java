package com.timecurrency.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.util.Calendar;

public class CurrencyManager {

    private static final String PREF_NAME = "TimeCurrencyPrefs";
    
    // Keys
    private static final String KEY_TOTAL_AMOUNT = "amount"; // Legacy key, serves as Total
    private static final String KEY_DAILY_AMOUNT = "daily_amount";
    private static final String KEY_LAST_RESET_TIME = "last_reset_time";
    
    // Settings Keys
    public static final String KEY_DISPLAY_MODE = "display_mode"; // 0 = Total, 1 = Daily
    public static final String KEY_VIBRATION_LEVEL = "vibration_level"; // 0 = Off, 1 = Light, 2 = Heavy

    // Broadcasts
    public static final String ACTION_UPDATE_UI = "com.timecurrency.app.ACTION_UPDATE_UI";
    public static final String EXTRA_AMOUNT = "com.timecurrency.app.EXTRA_AMOUNT";
    public static final String EXTRA_MODE_LABEL = "com.timecurrency.app.EXTRA_MODE_LABEL";

    // Constants
    public static final int MODE_TOTAL = 0;
    public static final int MODE_DAILY = 1;
    
    public static final int VIB_OFF = 0;
    public static final int VIB_LIGHT = 1;
    public static final int VIB_HEAVY = 2;

    public static int getCurrency(Context context) {
        checkDailyReset(context);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_DISPLAY_MODE, MODE_TOTAL);
        
        if (mode == MODE_DAILY) {
            return prefs.getInt(KEY_DAILY_AMOUNT, 0);
        } else {
            return prefs.getInt(KEY_TOTAL_AMOUNT, 0);
        }
    }
    
    public static String getModeLabel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_DISPLAY_MODE, MODE_TOTAL);
        return (mode == MODE_DAILY) ? "TODAY $" : "TOTAL $";
    }
    
    public static void toggleMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentMode = prefs.getInt(KEY_DISPLAY_MODE, MODE_TOTAL);
        int newMode = (currentMode == MODE_TOTAL) ? MODE_DAILY : MODE_TOTAL;
        prefs.edit().putInt(KEY_DISPLAY_MODE, newMode).apply();
        notifyUpdates(context, getCurrency(context));
    }
    
    public static int cycleVibration(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_VIBRATION_LEVEL, VIB_LIGHT);
        int next = (current + 1) % 3;
        prefs.edit().putInt(KEY_VIBRATION_LEVEL, next).apply();
        triggerVibration(context, true); // Demo vibration
        return next;
    }
    
    public static int getVibrationLevel(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_VIBRATION_LEVEL, VIB_LIGHT);
    }

    public static void updateCurrency(Context context, int delta) {
        checkDailyReset(context);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Update Total
        int currentTotal = prefs.getInt(KEY_TOTAL_AMOUNT, 0);
        int newTotal = currentTotal + delta;
        
        // Update Daily
        int currentDaily = prefs.getInt(KEY_DAILY_AMOUNT, 0);
        int newDaily = currentDaily + delta;

        prefs.edit()
            .putInt(KEY_TOTAL_AMOUNT, newTotal)
            .putInt(KEY_DAILY_AMOUNT, newDaily)
            .apply();
            
        // Log to Database for History/Export
        TransactionDbHelper.logTransaction(context, delta, newTotal);

        triggerVibration(context, false);

        int displayValue = (prefs.getInt(KEY_DISPLAY_MODE, MODE_TOTAL) == MODE_DAILY) ? newDaily : newTotal;
        notifyUpdates(context, displayValue);
    }
    
    /**
     * Recalculates the current Total and Daily amounts based on the full transaction history in DB.
     * Useful after importing data.
     */
    public static void recalculateTotals(Context context) {
        int total = TransactionDbHelper.calculateTotalBalance(context);
        
        long dailyStart = getDailyCycleStartTime();
        int daily = TransactionDbHelper.calculateDailyBalance(context, dailyStart);
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_TOTAL_AMOUNT, total)
            .putInt(KEY_DAILY_AMOUNT, daily)
            // Ensure we don't accidentally reset daily amount immediately
            .putLong(KEY_LAST_RESET_TIME, System.currentTimeMillis()) 
            .apply();
            
        int displayValue = (prefs.getInt(KEY_DISPLAY_MODE, MODE_TOTAL) == MODE_DAILY) ? daily : total;
        notifyUpdates(context, displayValue);
    }
    
    private static long getDailyCycleStartTime() {
        Calendar now = Calendar.getInstance();
        Calendar cycleStart = Calendar.getInstance();
        cycleStart.set(Calendar.HOUR_OF_DAY, 6);
        cycleStart.set(Calendar.MINUTE, 0);
        cycleStart.set(Calendar.SECOND, 0);
        cycleStart.set(Calendar.MILLISECOND, 0);
        
        if (now.before(cycleStart)) {
            cycleStart.add(Calendar.DAY_OF_YEAR, -1);
        }
        return cycleStart.getTimeInMillis();
    }
    
    private static void checkDailyReset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastReset = prefs.getLong(KEY_LAST_RESET_TIME, 0);
        
        long cycleStartTime = getDailyCycleStartTime();
        
        // If last reset was before the current cycle start, we need to reset
        if (lastReset < cycleStartTime) {
            prefs.edit()
                .putInt(KEY_DAILY_AMOUNT, 0)
                .putLong(KEY_LAST_RESET_TIME, System.currentTimeMillis())
                .apply();
        }
    }

    private static void notifyUpdates(Context context, int displayValue) {
        // 1. Notify Widget
        Intent widgetIntent = new Intent(context, CurrencyWidgetProvider.class);
        widgetIntent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        context.sendBroadcast(widgetIntent);
        
        // 2. Notify Activity UI
        Intent broadcastIntent = new Intent(ACTION_UPDATE_UI);
        broadcastIntent.putExtra(EXTRA_AMOUNT, displayValue);
        broadcastIntent.putExtra(EXTRA_MODE_LABEL, getModeLabel(context));
        context.sendBroadcast(broadcastIntent);

        // 3. Notify Notification
        NotificationService.refreshNotification(context);
    }
    
    private static void triggerVibration(Context context, boolean force) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int level = prefs.getInt(KEY_VIBRATION_LEVEL, VIB_LIGHT);
        
        if (level == VIB_OFF && !force) return;
        
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (level == VIB_HEAVY) {
                    v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    // Light tick
                    v.vibrate(VibrationEffect.createOneShot(10, 50)); 
                }
            } else {
                // Legacy
                if (level == VIB_HEAVY) {
                    v.vibrate(40);
                } else {
                    v.vibrate(10);
                }
            }
        }
    }
}