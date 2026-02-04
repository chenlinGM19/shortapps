package com.shortapps.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    
    private static final int REQUEST_PICK_SHORTCUT = 1001;
    private static final int REQUEST_CREATE_SHORTCUT = 1002;

    private WindowConfig config;
    private int configIndex;
    private List<WindowConfig> allConfigs;
    
    private EditText etName, etTriggerColor;
    private View viewTriggerColorPreview;
    private SwitchMaterial switchNotification, switchTrigger;
    private SeekBar sbColumns, sbTriggerWidth, sbTriggerHeight, sbTriggerRadius;
    private TextView tvColumns, tvTriggerWidth, tvTriggerHeight, tvTriggerRadius;
    private View layoutTriggerSettings;
    
    private RecyclerView recyclerItems;
    private ItemAdapter itemAdapter;
    
    private ShortcutItem pendingEditingItem = null; // Used when picking a shortcut to update an existing item

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
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
        
        tvTriggerWidth = findViewById(R.id.tvTriggerWidth);
        sbTriggerWidth = findViewById(R.id.sbTriggerWidth);
        sbTriggerWidth.setProgress(config.getTriggerWidth());
        tvTriggerWidth.setText("Width: " + config.getTriggerWidth() + "dp");

        tvTriggerHeight = findViewById(R.id.tvTriggerHeight);
        sbTriggerHeight = findViewById(R.id.sbTriggerHeight);
        sbTriggerHeight.setProgress(config.getTriggerHeight());
        tvTriggerHeight.setText("Height: " + config.getTriggerHeight() + "dp");
        
        tvTriggerRadius = findViewById(R.id.tvTriggerRadius);
        sbTriggerRadius = findViewById(R.id.sbTriggerRadius);
        sbTriggerRadius.setProgress(config.getTriggerRadius());
        tvTriggerRadius.setText("Corner Radius: " + config.getTriggerRadius() + "dp");
        
        etTriggerColor = findViewById(R.id.etTriggerColor);
        viewTriggerColorPreview = findViewById(R.id.viewTriggerColorPreview);
        String hex = String.format("#%08X", (0xFFFFFFFF & config.getTriggerColor()));
        etTriggerColor.setText(hex);
        updateColorPreview(config.getTriggerColor());
    }
    
    private void updateColorPreview(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(12);
        d.setColor(color);
        d.setStroke(2, Color.WHITE);
        viewTriggerColorPreview.setBackground(d);
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
        
        sbTriggerWidth.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerWidth(val);
            tvTriggerWidth.setText("Width: " + val + "dp");
        }));

        sbTriggerHeight.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerHeight(val);
            tvTriggerHeight.setText("Height: " + val + "dp");
        }));
        
        sbTriggerRadius.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerRadius(val);
            tvTriggerRadius.setText("Corner Radius: " + val + "dp");
        }));
        
        etTriggerColor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    int c = Color.parseColor(s.toString());
                    config.setTriggerColor(c);
                    updateColorPreview(c);
                } catch (Exception e) {}
            }
        });
        
        findViewById(R.id.btnAddApp).setOnClickListener(v -> showAppPicker(null));
        findViewById(R.id.btnAddShortcut).setOnClickListener(v -> showShortcutTypeDialog(null));
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
        
        Intent i = new Intent(this, OverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        finish();
    }
    
    // --- Logic: Add/Edit Items ---
    
    private void showAppPicker(@Nullable ShortcutItem editingItem) {
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
            .setTitle(editingItem == null ? "Select App" : "Change App")
            .setItems(names.toArray(new String[0]), (dialog, which) -> {
                ApplicationInfo selected = validApps.get(which);
                
                ShortcutItem item = editingItem;
                if (item == null) {
                    item = new ShortcutItem(java.util.UUID.randomUUID().toString(), ShortcutItem.TYPE_APP, names.get(which));
                } else {
                    item.setLabel(names.get(which));
                }
                item.setPackageName(selected.packageName);
                
                if (editingItem == null) {
                    askDisplayMode(item);
                } else {
                    itemAdapter.notifyDataSetChanged();
                }
            })
            .show();
    }
    
    private void showShortcutTypeDialog(@Nullable ShortcutItem editingItem) {
        String[] options = {"Tasker Task", "System Shortcut"};
        new AlertDialog.Builder(this)
            .setTitle("Select Type")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    showTaskerDialog(editingItem);
                } else {
                    launchSystemShortcutPicker(editingItem);
                }
            })
            .show();
    }

    private void showTaskerDialog(@Nullable ShortcutItem editingItem) {
        EditText input = new EditText(this);
        input.setHint("Task Name");
        if (editingItem != null) input.setText(editingItem.getTaskerTaskName());
        
        new AlertDialog.Builder(this)
            .setTitle("Enter Tasker Task Name")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                ShortcutItem item = editingItem;
                String taskName = input.getText().toString();
                if (item == null) {
                    item = new ShortcutItem(java.util.UUID.randomUUID().toString(), ShortcutItem.TYPE_TASKER, taskName);
                } else {
                    item.setLabel(taskName);
                }
                item.setTaskerTaskName(taskName);
                
                if (editingItem == null) {
                    askDisplayMode(item);
                } else {
                    itemAdapter.notifyDataSetChanged();
                }
            })
            .show();
    }
    
    private void launchSystemShortcutPicker(@Nullable ShortcutItem editingItem) {
        this.pendingEditingItem = editingItem;
        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_CREATE_SHORTCUT));
        pickIntent.putExtra(Intent.EXTRA_TITLE, "Select Shortcut");
        startActivityForResult(pickIntent, REQUEST_PICK_SHORTCUT);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        
        if (requestCode == REQUEST_PICK_SHORTCUT && data != null) {
            // User selected an activity that creates shortcuts. Now launch it.
            startActivityForResult(data, REQUEST_CREATE_SHORTCUT);
        }
        else if (requestCode == REQUEST_CREATE_SHORTCUT && data != null) {
            // The activity returned the shortcut intent
            Intent shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            
            if (shortcutIntent != null) {
                ShortcutItem item = pendingEditingItem;
                if (item == null) {
                    item = new ShortcutItem(java.util.UUID.randomUUID().toString(), ShortcutItem.TYPE_SHORTCUT, name);
                } else {
                    item.setLabel(name);
                }
                item.setIntentUri(shortcutIntent.toUri(0));
                
                if (pendingEditingItem == null) {
                    askDisplayMode(item);
                } else {
                    itemAdapter.notifyDataSetChanged();
                }
            }
            pendingEditingItem = null;
        }
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
    
    // --- Adapters ---
    
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false); 
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            ShortcutItem item = config.getItems().get(position);
            holder.tvName.setText(item.getLabel());
            String typeStr = "App";
            if (item.getType() == ShortcutItem.TYPE_TASKER) typeStr = "Tasker";
            if (item.getType() == ShortcutItem.TYPE_SHORTCUT) typeStr = "Shortcut";
            holder.tvInfo.setText(typeStr);
            
            if(item.getDisplayMode() == ShortcutItem.MODE_COLOR_BLOCK) {
                holder.colorIndicator.setVisibility(View.VISIBLE);
                holder.colorIndicator.setBackgroundColor(item.getColorInfo());
            } else {
                holder.colorIndicator.setVisibility(View.GONE);
            }
            
            holder.itemView.setOnClickListener(v -> {
                // Edit Logic
                if (item.getType() == ShortcutItem.TYPE_APP) showAppPicker(item);
                else if (item.getType() == ShortcutItem.TYPE_TASKER) showTaskerDialog(item);
                else if (item.getType() == ShortcutItem.TYPE_SHORTCUT) launchSystemShortcutPicker(item);
            });
        }

        @Override public int getItemCount() { return config.getItems().size(); }
        
        class Holder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo;
            View colorIndicator;
            Holder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvDelta);
                tvInfo = v.findViewById(R.id.tvDate);
                colorIndicator = new View(v.getContext());
                
                ViewGroup vg = (ViewGroup) v;
                vg.addView(colorIndicator, 0);
                colorIndicator.getLayoutParams().width = 50;
                colorIndicator.getLayoutParams().height = 50;
            }
        }
    }
}