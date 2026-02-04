package com.shortapps.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shortapps.app.model.WindowConfig;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String PREF_NAME = "ShortappsConfig";
    private static final String KEY_WINDOWS = "window_configs";
    private static final String KEY_TRIGGER_SIZE = "trigger_size";

    public static void saveWindows(Context context, List<WindowConfig> windows) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(windows);
        prefs.edit().putString(KEY_WINDOWS, json).apply();
    }

    public static List<WindowConfig> loadWindows(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_WINDOWS, null);
        if (json == null) return new ArrayList<>();
        
        Type type = new TypeToken<List<WindowConfig>>(){}.getType();
        return new Gson().fromJson(json, type);
    }
    
    public static void saveTriggerSize(Context context, int size) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TRIGGER_SIZE, size).apply();
    }
    
    public static int loadTriggerSize(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TRIGGER_SIZE, 60);
    }
}