package com.shortapps.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static final String PREF_NAME = "ShortappsData";
    private static final String KEY_WINDOWS = "windows";
    private static final String KEY_PILL_SIZE = "pill_size";

    public static List<DataModel.WindowConfig> loadWindows(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_WINDOWS, "[]");
        Type listType = new TypeToken<ArrayList<DataModel.WindowConfig>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    public static void saveWindows(Context context, List<DataModel.WindowConfig> windows) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(windows);
        prefs.edit().putString(KEY_WINDOWS, json).apply();
    }
    
    public static DataModel.WindowConfig getWindowById(Context context, String id) {
        List<DataModel.WindowConfig> list = loadWindows(context);
        for (DataModel.WindowConfig w : list) {
            if (w.id.equals(id)) return w;
        }
        return null;
    }

    public static int getPillSize(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_PILL_SIZE, 60); // dp
    }

    public static void savePillSize(Context context, int dp) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_PILL_SIZE, dp).apply();
    }
}