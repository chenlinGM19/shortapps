package com.timecurrency.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    
    // UI References
    private View previewContainer;
    private TextView previewAmount;
    private Button previewPlus;
    private Button previewMinus;
    
    private ImageView previewBgImage;
    private View previewBgColor;
    
    private MaterialButtonToggleGroup toggleBgType;
    private LinearLayout sectionColor;
    private LinearLayout sectionImage;
    private RadioGroup rgStyles;
    private SeekBar seekBarTransparency;
    private SeekBar seekBarRadius;
    
    private String selectedImagePath = null;
    
    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    saveImageLocally(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_widget_config);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        initViews();
        loadSavedSettings();
        
        setupDragListener(previewAmount);
        setupDragListener(previewPlus);
        setupDragListener(previewMinus);
        
        updateUIState();
    }
    
    private void initViews() {
        previewContainer = findViewById(R.id.previewContainer);
        previewAmount = findViewById(R.id.previewAmount);
        previewPlus = findViewById(R.id.previewPlus);
        previewMinus = findViewById(R.id.previewMinus);
        
        previewBgImage = findViewById(R.id.previewBgImage);
        previewBgColor = findViewById(R.id.previewBgColor);
        
        toggleBgType = findViewById(R.id.toggleBgType);
        sectionColor = findViewById(R.id.sectionColor);
        sectionImage = findViewById(R.id.sectionImage);
        
        rgStyles = findViewById(R.id.rgStyles);
        seekBarTransparency = findViewById(R.id.seekBarTransparency);
        seekBarRadius = findViewById(R.id.seekBarRadius);
        
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnClearImage = findViewById(R.id.btnClearImage);
        Button btnSave = findViewById(R.id.btnSaveWidget);
        
        btnSelectImage.setOnClickListener(v -> pickImage.launch("image/*"));
        btnClearImage.setOnClickListener(v -> {
            selectedImagePath = null;
            previewBgImage.setImageDrawable(null);
        });
        
        toggleBgType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateUIState();
        });
        
        rgStyles.setOnCheckedChangeListener((g, i) -> refreshPreviewColor());
        seekBarTransparency.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { refreshPreviewColor(); }
        });
        
        seekBarRadius.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { refreshPreviewRadius(); }
        });
        
        btnSave.setOnClickListener(v -> saveAndFinish());
    }
    
    private void setupDragListener(View targetView) {
        targetView.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        
                        // Constrain dragging
                        float limitX = previewContainer.getWidth() - view.getWidth();
                        float limitY = previewContainer.getHeight() - view.getHeight();
                        
                        // Loose constraints
                        if (newX < -view.getWidth()/2) newX = -view.getWidth()/2;
                        if (newX > previewContainer.getWidth() - view.getWidth()/2) newX = previewContainer.getWidth() - view.getWidth()/2;
                        if (newY < -view.getHeight()/2) newY = -view.getHeight()/2;
                        if (newY > previewContainer.getHeight() - view.getHeight()/2) newY = previewContainer.getHeight() - view.getHeight()/2;

                        view.setX(newX);
                        view.setY(newY);
                        return true;
                        
                    default:
                        return false;
                }
            }
        });
    }

    private void loadSavedSettings() {
        int type = WidgetSettingsHelper.loadBackgroundType(this, appWidgetId);
        toggleBgType.check(type == 1 ? R.id.btnTypeImage : R.id.btnTypeColor);
        
        int style = WidgetSettingsHelper.loadStyle(this, appWidgetId);
        if (style == 0) rgStyles.check(R.id.style1);
        else if (style == 1) rgStyles.check(R.id.style2);
        else if (style == 2) rgStyles.check(R.id.style3);
        else if (style == 4) rgStyles.check(R.id.style5);
        
        seekBarTransparency.setProgress(WidgetSettingsHelper.loadTransparency(this, appWidgetId));
        seekBarRadius.setProgress(WidgetSettingsHelper.loadCornerRadius(this, appWidgetId));
        
        selectedImagePath = WidgetSettingsHelper.loadImagePath(this, appWidgetId);
        if (selectedImagePath != null) {
            Bitmap b = BitmapFactory.decodeFile(selectedImagePath);
            previewBgImage.setImageBitmap(b);
        }
        
        // Load Offsets for 3 components
        final int[] amountOff = WidgetSettingsHelper.loadAmountOffset(this, appWidgetId);
        final int[] plusOff = WidgetSettingsHelper.loadPlusOffset(this, appWidgetId);
        final int[] minusOff = WidgetSettingsHelper.loadMinusOffset(this, appWidgetId);
        
        // Apply offsets to preview immediately
        previewContainer.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            
            // NOTE: FrameLayout with layout_gravity=center places them at (W-w)/2, (H-h)/2.
            // Translation is additive to that position.
            // The saved offsets are displacement from Center in DP.
            
            previewAmount.setTranslationX(amountOff[0] * density);
            previewAmount.setTranslationY(amountOff[1] * density);
            
            previewPlus.setTranslationX(plusOff[0] * density);
            previewPlus.setTranslationY(plusOff[1] * density);
            
            previewMinus.setTranslationX(minusOff[0] * density);
            previewMinus.setTranslationY(minusOff[1] * density);
        });
        
        refreshPreviewColor();
        refreshPreviewRadius();
    }
    
    private void updateUIState() {
        boolean isImage = toggleBgType.getCheckedButtonId() == R.id.btnTypeImage;
        if (isImage) {
            sectionColor.setVisibility(View.GONE);
            sectionImage.setVisibility(View.VISIBLE);
            previewBgColor.setVisibility(View.GONE);
            previewBgImage.setVisibility(View.VISIBLE);
        } else {
            sectionColor.setVisibility(View.VISIBLE);
            sectionImage.setVisibility(View.GONE);
            previewBgColor.setVisibility(View.VISIBLE);
            previewBgImage.setVisibility(View.GONE);
        }
    }
    
    private void refreshPreviewColor() {
        int alpha = seekBarTransparency.getProgress();
        int color = Color.BLACK;
        
        int id = rgStyles.getCheckedRadioButtonId();
        if (id == R.id.style1) color = Color.parseColor("#212121");
        else if (id == R.id.style2) color = Color.WHITE;
        else if (id == R.id.style3) color = Color.parseColor("#03DAC6");
        else if (id == R.id.style5) color = Color.parseColor("#000000");
        
        int finalColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        previewBgColor.setBackgroundColor(finalColor);
        
        // Update Text Colors based on style
        int textColor = (id == R.id.style2 || id == R.id.style3) ? Color.BLACK : Color.WHITE;
        previewAmount.setTextColor(textColor);
        previewPlus.setTextColor(textColor);
        previewMinus.setTextColor(textColor);
    }
    
    private void refreshPreviewRadius() {
        float radius = seekBarRadius.getProgress() * getResources().getDisplayMetrics().density;
        previewContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    private void saveAndFinish() {
        int bgType = (toggleBgType.getCheckedButtonId() == R.id.btnTypeImage) ? 1 : 0;
        WidgetSettingsHelper.saveBackgroundType(this, appWidgetId, bgType);
        
        int style = 0;
        int styleId = rgStyles.getCheckedRadioButtonId();
        if (styleId == R.id.style2) style = 1;
        else if (styleId == R.id.style3) style = 2;
        else if (styleId == R.id.style5) style = 4;
        WidgetSettingsHelper.saveStyle(this, appWidgetId, style);
        
        WidgetSettingsHelper.saveTransparency(this, appWidgetId, seekBarTransparency.getProgress());
        WidgetSettingsHelper.saveCornerRadius(this, appWidgetId, seekBarRadius.getProgress());
        WidgetSettingsHelper.saveImagePath(this, appWidgetId, selectedImagePath);
        
        // Save offsets for each element
        saveElementOffset(previewAmount, "amount");
        saveElementOffset(previewPlus, "plus");
        saveElementOffset(previewMinus, "minus");

        // Update Widget
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        CurrencyWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
    
    private void saveElementOffset(View view, String type) {
        float centerX = previewContainer.getWidth() / 2f;
        float centerY = previewContainer.getHeight() / 2f;
        float viewCenterX = view.getX() + view.getWidth() / 2f;
        float viewCenterY = view.getY() + view.getHeight() / 2f;
        
        float density = getResources().getDisplayMetrics().density;
        int offX = (int) ((viewCenterX - centerX) / density);
        int offY = (int) ((viewCenterY - centerY) / density);
        
        if (type.equals("amount")) WidgetSettingsHelper.saveAmountOffset(this, appWidgetId, offX, offY);
        else if (type.equals("plus")) WidgetSettingsHelper.savePlusOffset(this, appWidgetId, offX, offY);
        else if (type.equals("minus")) WidgetSettingsHelper.saveMinusOffset(this, appWidgetId, offX, offY);
    }
    
    private void saveImageLocally(Uri sourceUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return;
            
            // Limit size - reduced to 512 to prevent TransactionTooLargeException
            int maxDim = 512;
            if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
                float ratio = (float)bitmap.getWidth()/bitmap.getHeight();
                int w = (ratio > 1) ? maxDim : (int)(maxDim*ratio);
                int h = (ratio > 1) ? (int)(maxDim/ratio) : maxDim;
                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
            }
            
            File file = new File(getFilesDir(), "widget_bg_" + appWidgetId + ".png");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
            
            selectedImagePath = file.getAbsolutePath();
            previewBgImage.setImageBitmap(bitmap);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}