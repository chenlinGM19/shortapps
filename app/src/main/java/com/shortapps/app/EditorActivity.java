package com.shortapps.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.shortapps.app.model.ShortcutItem;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class EditorActivity extends AppCompatActivity {
    
    private WindowConfig config;
    private int configIndex;
    private List<WindowConfig> allConfigs;
    
    private EditText etName;
    private SeekBar sbColumns;
    private TextView tvColumns;
    private TextView tvItemCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        allConfigs = ConfigManager.loadWindows(this);
        configIndex = getIntent().getIntExtra("window_index", -1);
        if (configIndex == -1) { finish(); return; }
        config = allConfigs.get(configIndex);
        
        etName = findViewById(R.id.etName);
        etName.setText(config.getName());
        
        tvColumns = findViewById(R.id.tvColumns);
        sbColumns = findViewById(R.id.sbColumns);
        sbColumns.setProgress(config.getColumns());
        tvColumns.setText("Columns: " + config.getColumns());
        
        sbColumns.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                if(p < 1) p = 1;
                config.setColumns(p);
                tvColumns.setText("Columns: " + p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        
        tvItemCount = findViewById(R.id.tvItemCount);
        updateCount();
        
        findViewById(R.id.btnAddApp).setOnClickListener(v -> showAppPicker());
        findViewById(R.id.btnAddTask).setOnClickListener(v -> addTaskerTask());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }
    
    private void updateCount() {
        tvItemCount.setText("Current Items: " + config.getItems().size());
    }
    
    private void save() {
        config.setName(etName.getText().toString());
        allConfigs.set(configIndex, config);
        ConfigManager.saveWindows(this, allConfigs);
        
        // Restart service
        Intent i = new Intent(this, OverlayService.class);
        startService(i);
        finish();
    }
    
    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<String> names = new ArrayList<>();
        List<ApplicationInfo> validApps = new ArrayList<>();
        
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                validApps.add(app);
                names.add(pm.getApplicationLabel(app).toString());
            }
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Select App")
            .setItems(names.toArray(new String[0]), (dialog, which) -> {
                ApplicationInfo selected = validApps.get(which);
                ShortcutItem item = new ShortcutItem(
                    java.util.UUID.randomUUID().toString(),
                    ShortcutItem.TYPE_APP,
                    names.get(which)
                );
                item.setPackageName(selected.packageName);
                askDisplayMode(item);
            })
            .show();
    }
    
    private void addTaskerTask() {
        EditText input = new EditText(this);
        input.setHint("Task Name");
        new AlertDialog.Builder(this)
            .setTitle("Enter Tasker Task Name")
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                ShortcutItem item = new ShortcutItem(
                    java.util.UUID.randomUUID().toString(),
                    ShortcutItem.TYPE_TASKER,
                    input.getText().toString()
                );
                item.setTaskerTaskName(input.getText().toString());
                askDisplayMode(item);
            })
            .show();
    }
    
    private void askDisplayMode(ShortcutItem item) {
        String[] options = {"Original Icon", "Colored Block"};
        new AlertDialog.Builder(this)
            .setTitle("Display Mode")
            .setItems(options, (d, which) -> {
                item.setDisplayMode(which);
                if (which == ShortcutItem.MODE_COLOR_BLOCK) {
                    item.setColorInfo(generateUniqueColor());
                }
                config.getItems().add(item);
                updateCount();
            })
            .show();
    }
    
    private int generateUniqueColor() {
        Random r = new Random();
        // Generate bright pastel/neon colors (HSV)
        float[] hsv = new float[3];
        hsv[0] = r.nextInt(360); // Hue
        hsv[1] = 0.6f + r.nextFloat() * 0.4f; // Saturation 60-100%
        hsv[2] = 0.8f + r.nextFloat() * 0.2f; // Value 80-100%
        return Color.HSVToColor(hsv);
    }
}