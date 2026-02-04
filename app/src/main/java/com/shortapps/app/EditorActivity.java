package com.shortapps.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
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
    private SwitchMaterial switchNotification, switchTrigger;
    private SeekBar sbColumns, sbTriggerSize, sbTriggerRadius;
    private TextView tvColumns, tvTriggerSize, tvTriggerRadius;
    private View layoutTriggerSettings;
    
    private RecyclerView recyclerItems;
    private ItemAdapter itemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        // Setup Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        allConfigs = ConfigManager.loadWindows(this);
        configIndex = getIntent().getIntExtra("window_index", -1);
        if (configIndex == -1) { finish(); return; }
        config = allConfigs.get(configIndex);
        
        bindViews();
        setupListeners();
        setupRecyclerView();
    }
    
    private void bindViews() {
        etName = findViewById(R.id.etName);
        etName.setText(config.getName());
        
        switchNotification = findViewById(R.id.switchNotification);
        switchNotification.setChecked(config.isEnabledInNotification());
        
        tvColumns = findViewById(R.id.tvColumns);
        sbColumns = findViewById(R.id.sbColumns);
        sbColumns.setProgress(config.getColumns());
        tvColumns.setText("Columns: " + config.getColumns());
        
        // Trigger Settings
        switchTrigger = findViewById(R.id.switchTrigger);
        switchTrigger.setChecked(config.isTriggerEnabled());
        
        layoutTriggerSettings = findViewById(R.id.layoutTriggerSettings);
        layoutTriggerSettings.setVisibility(config.isTriggerEnabled() ? View.VISIBLE : View.GONE);
        
        tvTriggerSize = findViewById(R.id.tvTriggerSize);
        sbTriggerSize = findViewById(R.id.sbTriggerSize);
        sbTriggerSize.setProgress(config.getTriggerSize());
        tvTriggerSize.setText("Size: " + config.getTriggerSize() + "dp");
        
        tvTriggerRadius = findViewById(R.id.tvTriggerRadius);
        sbTriggerRadius = findViewById(R.id.sbTriggerRadius);
        sbTriggerRadius.setProgress(config.getTriggerRadius());
        tvTriggerRadius.setText("Corner Radius: " + config.getTriggerRadius() + "dp");
    }
    
    private void setupListeners() {
        sbColumns.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
             config.setColumns(val);
             tvColumns.setText("Columns: " + val);
        }));
        
        switchTrigger.setOnCheckedChangeListener((btn, checked) -> {
            config.setTriggerEnabled(checked);
            layoutTriggerSettings.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        
        sbTriggerSize.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerSize(val);
            tvTriggerSize.setText("Size: " + val + "dp");
        }));
        
        sbTriggerRadius.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerRadius(val);
            tvTriggerRadius.setText("Corner Radius: " + val + "dp");
        }));
        
        findViewById(R.id.btnAddApp).setOnClickListener(v -> showAppPicker());
        findViewById(R.id.btnAddTask).setOnClickListener(v -> addTaskerTask());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }
    
    private void setupRecyclerView() {
        recyclerItems = findViewById(R.id.recyclerItems);
        recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ItemAdapter();
        recyclerItems.setAdapter(itemAdapter);
        
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(config.getItems(), from, to);
                itemAdapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                config.getItems().remove(pos);
                itemAdapter.notifyItemRemoved(pos);
            }
        };
        new ItemTouchHelper(simpleCallback).attachToRecyclerView(recyclerItems);
    }
    
    private void save() {
        config.setName(etName.getText().toString());
        config.setEnabledInNotification(switchNotification.isChecked());
        
        allConfigs.set(configIndex, config);
        ConfigManager.saveWindows(this, allConfigs);
        
        // Restart service
        Intent i = new Intent(this, OverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        finish();
    }
    
    // --- Helper Methods ---
    
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
                itemAdapter.notifyItemInserted(config.getItems().size() - 1);
            })
            .show();
    }
    
    private int generateUniqueColor() {
        Random r = new Random();
        float[] hsv = new float[3];
        hsv[0] = r.nextInt(360);
        hsv[1] = 0.6f + r.nextFloat() * 0.4f;
        hsv[2] = 0.8f + r.nextFloat() * 0.2f;
        return Color.HSVToColor(hsv);
    }
    
    // --- Inner Classes ---
    
    private interface OnProgress { void onP(int v); }
    private static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        OnProgress op;
        SimpleSeekBarListener(OnProgress op) { this.op = op; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean u) { op.onP(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
    
    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false); // Reusing layout
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            ShortcutItem item = config.getItems().get(position);
            holder.tvName.setText(item.getLabel());
            holder.tvInfo.setText(item.getType() == ShortcutItem.TYPE_APP ? "App" : "Tasker");
            
            // Visual indicator of mode
            if(item.getDisplayMode() == ShortcutItem.MODE_COLOR_BLOCK) {
                holder.colorIndicator.setVisibility(View.VISIBLE);
                holder.colorIndicator.setBackgroundColor(item.getColorInfo());
            } else {
                holder.colorIndicator.setVisibility(View.GONE);
            }
        }

        @Override public int getItemCount() { return config.getItems().size(); }
        
        class Holder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo;
            View colorIndicator;
            Holder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvDelta); // Reusing ID
                tvInfo = v.findViewById(R.id.tvDate); // Reusing ID
                colorIndicator = new View(v.getContext());
                
                // Add color indicator dynamically since reusing layout
                ViewGroup vg = (ViewGroup) v;
                vg.addView(colorIndicator, 0);
                colorIndicator.getLayoutParams().width = 50;
                colorIndicator.getLayoutParams().height = 50;
            }
        }
    }
}