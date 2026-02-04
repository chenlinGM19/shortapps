package com.timecurrency.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public class WidgetSettingsHelper {
    private static final String PREFS_NAME = "com.timecurrency.app.WidgetPrefs";
    private static final String PREF_PREFIX_KEY = "appwidget_";

    // --- Background & Style ---

    public static void saveBackgroundType(Context context, int appWidgetId, int type) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_bg_type", type);
        prefs.apply();
    }

    public static int loadBackgroundType(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_bg_type", 0); 
    }

    public static void saveStyle(Context context, int appWidgetId, int style) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_style", style);
        prefs.apply();
    }

    public static int loadStyle(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_style", 0);
    }

    public static void saveTransparency(Context context, int appWidgetId, int alpha) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_alpha", alpha);
        prefs.apply();
    }

    public static int loadTransparency(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_alpha", 255);
    }
    
    public static void saveImagePath(Context context, int appWidgetId, String path) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_image", path);
        prefs.apply();
    }

    public static String loadImagePath(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_image", null);
    }
    
    public static void saveCornerRadius(Context context, int appWidgetId, int radius) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_radius", radius);
        prefs.apply();
    }

    public static int loadCornerRadius(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_radius", 16); 
    }

    // --- Independent Positioning (Amount, Plus, Minus) ---

    // Generic helper for offset saving
    private static void saveOffset(Context context, int appWidgetId, String element, int x, int y) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_" + element + "_x", x);
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + "_" + element + "_y", y);
        prefs.apply();
    }

    private static int[] loadOffset(Context context, int appWidgetId, String element) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        int x = prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_" + element + "_x", 0);
        int y = prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_" + element + "_y", 0);
        return new int[]{x, y};
    }

    public static void saveAmountOffset(Context context, int appWidgetId, int x, int y) {
        saveOffset(context, appWidgetId, "amount", x, y);
    }
    public static int[] loadAmountOffset(Context context, int appWidgetId) {
        return loadOffset(context, appWidgetId, "amount");
    }

    public static void savePlusOffset(Context context, int appWidgetId, int x, int y) {
        saveOffset(context, appWidgetId, "plus", x, y);
    }
    
    public static int[] loadPlusOffset(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        if (!prefs.contains(PREF_PREFIX_KEY + appWidgetId + "_plus_x")) {
            // Initial default offset for Plus: Right side
            return new int[]{60, 0}; 
        }
        return loadOffset(context, appWidgetId, "plus");
    }

    public static void saveMinusOffset(Context context, int appWidgetId, int x, int y) {
        saveOffset(context, appWidgetId, "minus", x, y);
    }
    
    public static int[] loadMinusOffset(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        if (!prefs.contains(PREF_PREFIX_KEY + appWidgetId + "_minus_x")) {
            // Initial default offset for Minus: Left side
            return new int[]{-60, 0}; 
        }
        return loadOffset(context, appWidgetId, "minus");
    }

    public static void deletePrefs(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Remove all keys starting with prefix + ID
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(PREF_PREFIX_KEY + appWidgetId)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}