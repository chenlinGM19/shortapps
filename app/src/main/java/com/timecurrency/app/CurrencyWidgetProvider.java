package com.timecurrency.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

public class CurrencyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_INCREMENT = "com.timecurrency.app.ACTION_INCREMENT";
    public static final String ACTION_DECREMENT = "com.timecurrency.app.ACTION_DECREMENT";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetSettingsHelper.deletePrefs(context, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (ACTION_INCREMENT.equals(action)) {
            CurrencyManager.updateCurrency(context, 1);
        } else if (ACTION_DECREMENT.equals(action)) {
            CurrencyManager.updateCurrency(context, -1);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context.getPackageName(), CurrencyWidgetProvider.class.getName());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        int amount = CurrencyManager.getCurrency(context);
        
        // Load settings
        int bgType = WidgetSettingsHelper.loadBackgroundType(context, appWidgetId); // 0=Color, 1=Image
        int style = WidgetSettingsHelper.loadStyle(context, appWidgetId);
        int alpha = WidgetSettingsHelper.loadTransparency(context, appWidgetId);
        String imagePath = WidgetSettingsHelper.loadImagePath(context, appWidgetId);
        int radiusDp = WidgetSettingsHelper.loadCornerRadius(context, appWidgetId);
        
        // Load Offsets for 3 elements
        int[] amountOff = WidgetSettingsHelper.loadAmountOffset(context, appWidgetId);
        int[] plusOff = WidgetSettingsHelper.loadPlusOffset(context, appWidgetId);
        int[] minusOff = WidgetSettingsHelper.loadMinusOffset(context, appWidgetId);

        // Always use the unified layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_amount, String.valueOf(amount));

        // --- 1. Corner Radius ---
        int bgRes = getBackgroundResourceForRadius(radiusDp);
        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes);

        // --- 2. Background Logic (Exclusive) ---
        if (bgType == 1 && imagePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_bg_image, bitmap);
                views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE);
                views.setViewVisibility(R.id.widget_bg_color, View.GONE);
            } else {
                views.setViewVisibility(R.id.widget_bg_image, View.GONE);
                views.setViewVisibility(R.id.widget_bg_color, View.VISIBLE);
                applyColorBackground(views, style, alpha);
            }
        } else {
            views.setViewVisibility(R.id.widget_bg_image, View.GONE);
            views.setViewVisibility(R.id.widget_bg_color, View.VISIBLE);
            applyColorBackground(views, style, alpha);
        }

        // --- 3. Style (Text Colors) ---
        applyTextStyle(views, style);

        // --- 4. Layout Positioning (Offsets) ---
        // Apply offsets by setting padding to the wrapper FrameLayouts
        float density = context.getResources().getDisplayMetrics().density;
        
        applyOffsetToWrapper(views, R.id.wrapper_amount, amountOff[0], amountOff[1], density);
        applyOffsetToWrapper(views, R.id.wrapper_plus, plusOff[0], plusOff[1], density);
        applyOffsetToWrapper(views, R.id.wrapper_minus, minusOff[0], minusOff[1], density);

        // --- 5. Actions ---
        setupActions(context, views, appWidgetId);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
    
    private static void applyOffsetToWrapper(RemoteViews views, int viewId, int offX, int offY, float density) {
        int pxX = (int) (offX * density);
        int pxY = (int) (offY * density);
        
        int padLeft = (pxX > 0) ? pxX : 0;
        int padRight = (pxX < 0) ? -pxX : 0;
        int padTop = (pxY > 0) ? pxY : 0;
        int padBottom = (pxY < 0) ? -pxY : 0;
        
        views.setViewPadding(viewId, padLeft, padTop, padRight, padBottom);
    }
    
    private static void applyColorBackground(RemoteViews views, int style, int alpha) {
        int color = Color.parseColor("#212121"); // Default Dark
        if (style == 1) color = Color.WHITE;
        else if (style == 2) color = Color.parseColor("#03DAC6");
        else if (style == 4) color = Color.BLACK;

        int finalColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        views.setInt(R.id.widget_bg_color, "setBackgroundColor", finalColor);
    }
    
    private static void applyTextStyle(RemoteViews views, int style) {
        int textColor = Color.WHITE;
        if (style == 1) textColor = Color.BLACK; // Light theme needs black text
        else if (style == 2) textColor = Color.BLACK; // Accent usually needs black text
        
        views.setTextColor(R.id.widget_amount, textColor);
        views.setTextColor(R.id.widget_btn_plus, textColor);
        views.setTextColor(R.id.widget_btn_minus, textColor);
        views.setInt(R.id.widget_btn_config, "setColorFilter", textColor);
    }

    private static void setupActions(Context context, RemoteViews views, int appWidgetId) {
        Intent incIntent = new Intent(context, CurrencyWidgetProvider.class);
        incIntent.setAction(ACTION_INCREMENT);
        PendingIntent pendingInc = PendingIntent.getBroadcast(context, 100, incIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_btn_plus, pendingInc);

        Intent decIntent = new Intent(context, CurrencyWidgetProvider.class);
        decIntent.setAction(ACTION_DECREMENT);
        PendingIntent pendingDec = PendingIntent.getBroadcast(context, 101, decIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_btn_minus, pendingDec);

        Intent configIntent = new Intent(context, WidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setData(Uri.parse(configIntent.toUri(Intent.URI_INTENT_SCHEME)));
        PendingIntent pendingConfig = PendingIntent.getActivity(context, appWidgetId, configIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_btn_config, pendingConfig);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(context, 102, openIntent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_amount, pendingOpen);
    }

    private static int getBackgroundResourceForRadius(int radiusDp) {
        if (radiusDp <= 4) return R.drawable.rounded_0dp;
        if (radiusDp <= 12) return R.drawable.rounded_8dp;
        if (radiusDp <= 20) return R.drawable.rounded_16dp;
        if (radiusDp <= 28) return R.drawable.rounded_24dp;
        if (radiusDp <= 36) return R.drawable.rounded_32dp;
        if (radiusDp <= 48) return R.drawable.rounded_48dp;
        return R.drawable.rounded_64dp;
    }
}