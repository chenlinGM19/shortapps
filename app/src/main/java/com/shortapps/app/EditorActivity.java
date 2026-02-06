package com.shortapps.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.shortapps.app.model.ShortcutItem;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;
import com.shortapps.app.view.ColorWheelView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class EditorActivity extends AppCompatActivity {
    
    private static final int REQUEST_PICK_SHORTCUT = 1001;
    private static final int REQUEST_CREATE_SHORTCUT = 1002;

    private WindowConfig config;
    private int configIndex;
    private List<WindowConfig> allConfigs;
    
    private EditText etName;
    private View viewTriggerColorPreview, previewTrigger;
    private SwitchMaterial switchNotification, switchTrigger, switchShowLabels;
    private SeekBar sbColumns, sbTriggerWidth, sbTriggerHeight;
    private SeekBar sbRadiusTL, sbRadiusTR, sbRadiusBL, sbRadiusBR;
    private TextView tvColumns, tvTriggerWidth, tvTriggerHeight;
    private MaterialButtonToggleGroup toggleTriggerStyle;
    private View layoutTriggerSettings;
    
    private RecyclerView recyclerItems;
    private ItemAdapter itemAdapter;
    
    private ShortcutItem pendingEditingItem = null;

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
        updatePreview();
    }
    
    private void bindViews() {
        etName = findViewById(R.id.etName);
        etName.setText(config.getName());
        
        switchNotification = findViewById(R.id.switchNotification);
        switchNotification.setChecked(config.isEnabledInNotification());
        
        switchShowLabels = findViewById(R.id.switchShowLabels);
        switchShowLabels.setChecked(config.isShowLabels());
        
        tvColumns = findViewById(R.id.tvColumns);
        sbColumns = findViewById(R.id.sbColumns);
        sbColumns.setProgress(config.getColumns());
        tvColumns.setText("Columns: " + config.getColumns());
        
        // Trigger Settings
        switchTrigger = findViewById(R.id.switchTrigger);
        switchTrigger.setChecked(config.isTriggerEnabled());
        
        layoutTriggerSettings = findViewById(R.id.layoutTriggerSettings);
        layoutTriggerSettings.setVisibility(config.isTriggerEnabled() ? View.VISIBLE : View.GONE);
        
        toggleTriggerStyle = findViewById(R.id.toggleTriggerStyle);
        int styleId = R.id.btnStyle0;
        switch (config.getTriggerStyle()) {
            case 1: styleId = R.id.btnStyle1; break;
            case 2: styleId = R.id.btnStyle2; break;
            case 3: styleId = R.id.btnStyle3; break;
        }
        toggleTriggerStyle.check(styleId);
        
        tvTriggerWidth = findViewById(R.id.tvTriggerWidth);
        sbTriggerWidth = findViewById(R.id.sbTriggerWidth);
        sbTriggerWidth.setProgress(config.getTriggerWidth());
        tvTriggerWidth.setText("Width: " + config.getTriggerWidth() + "dp");

        tvTriggerHeight = findViewById(R.id.tvTriggerHeight);
        sbTriggerHeight = findViewById(R.id.sbTriggerHeight);
        sbTriggerHeight.setProgress(config.getTriggerHeight());
        tvTriggerHeight.setText("Height: " + config.getTriggerHeight() + "dp");
        
        sbRadiusTL = findViewById(R.id.sbRadiusTL);
        sbRadiusTR = findViewById(R.id.sbRadiusTR);
        sbRadiusBL = findViewById(R.id.sbRadiusBL);
        sbRadiusBR = findViewById(R.id.sbRadiusBR);
        
        sbRadiusTL.setProgress(config.getRadiusTL());
        sbRadiusTR.setProgress(config.getRadiusTR());
        sbRadiusBL.setProgress(config.getRadiusBL());
        sbRadiusBR.setProgress(config.getRadiusBR());
        
        viewTriggerColorPreview = findViewById(R.id.viewTriggerColorPreview);
        previewTrigger = findViewById(R.id.previewTrigger);
        
        updateColorPreview(config.getTriggerColor());
    }
    
    private void updateColorPreview(int color) {
        viewTriggerColorPreview.setBackgroundColor(color);
    }
    
    private void updatePreview() {
        if (previewTrigger == null) return;
        
        float d = getResources().getDisplayMetrics().density;
        int w = (int) (config.getTriggerWidth() * d);
        int h = (int) (config.getTriggerHeight() * d);
        
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewTrigger.getLayoutParams();
        lp.width = w;
        lp.height = h;
        previewTrigger.setLayoutParams(lp);
        
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        
        // Set individual corner radii: TL, TR, BR, BL (each pair X/Y)
        float[] radii = new float[] {
            config.getRadiusTL() * d, config.getRadiusTL() * d,
            config.getRadiusTR() * d, config.getRadiusTR() * d,
            config.getRadiusBR() * d, config.getRadiusBR() * d,
            config.getRadiusBL() * d, config.getRadiusBL() * d
        };
        shape.setCornerRadii(radii);
        
        int color = config.getTriggerColor();
        int style = config.getTriggerStyle();
        
        switch (style) {
            case 0: // Solid
                shape.setColor(color);
                break;
            case 1: // Outline
                shape.setColor(Color.TRANSPARENT);
                shape.setStroke(4, color);
                break;
            case 2: // Glass
                shape.setColor(Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)));
                shape.setStroke(2, Color.WHITE);
                break;
            case 3: // Inverted
                shape.setColor(Color.WHITE);
                shape.setStroke(4, color);
                break;
            default:
                shape.setColor(color);
        }
        
        previewTrigger.setBackground(shape);
    }
    
    private void setupListeners() {
        sbColumns.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
             config.setColumns(val);
             tvColumns.setText("Columns: " + val);
        }));
        
        switchNotification.setOnCheckedChangeListener((btn, checked) -> config.setEnabledInNotification(checked));
        switchShowLabels.setOnCheckedChangeListener((btn, checked) -> config.setShowLabels(checked));
        
        switchTrigger.setOnCheckedChangeListener((btn, checked) -> {
            config.setTriggerEnabled(checked);
            layoutTriggerSettings.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        
        toggleTriggerStyle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnStyle0) config.setTriggerStyle(0);
                else if (checkedId == R.id.btnStyle1) config.setTriggerStyle(1);
                else if (checkedId == R.id.btnStyle2) config.setTriggerStyle(2);
                else if (checkedId == R.id.btnStyle3) config.setTriggerStyle(3);
                updatePreview();
            }
        });
        
        sbTriggerWidth.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerWidth(val);
            tvTriggerWidth.setText("Width: " + val + "dp");
            updatePreview();
        }));

        sbTriggerHeight.setOnSeekBarChangeListener(new SimpleSeekBarListener(val -> {
            config.setTriggerHeight(val);
            tvTriggerHeight.setText("Height: " + val + "dp");
            updatePreview();
        }));
        
        SimpleSeekBarListener radiusListener = new SimpleSeekBarListener(val -> {
             config.setRadiusTL(sbRadiusTL.getProgress());
             config.setRadiusTR(sbRadiusTR.getProgress());
             config.setRadiusBL(sbRadiusBL.getProgress());
             config.setRadiusBR(sbRadiusBR.getProgress());
             updatePreview();
        });
        
        sbRadiusTL.setOnSeekBarChangeListener(radiusListener);
        sbRadiusTR.setOnSeekBarChangeListener(radiusListener);
        sbRadiusBL.setOnSeekBarChangeListener(radiusListener);
        sbRadiusBR.setOnSeekBarChangeListener(radiusListener);
        
        findViewById(R.id.btnPickColor).setOnClickListener(v -> showColorWheelDialog());
        
        findViewById(R.id.btnAddApp).setOnClickListener(v -> showAppPicker(null));
        findViewById(R.id.btnAddShortcut).setOnClickListener(v -> showShortcutTypeDialog(null));
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }
    
    private void showColorWheelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick Color");
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);
        
        ColorWheelView wheel = new ColorWheelView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600);
        container.addView(wheel, lp);
        
        // Alpha Slider
        TextView labelAlpha = new TextView(this);
        labelAlpha.setText("Transparency");
        labelAlpha.setTextColor(Color.WHITE);
        container.addView(labelAlpha);
        
        SeekBar alphaBar = new SeekBar(this);
        alphaBar.setMax(255);
        alphaBar.setProgress(Color.alpha(config.getTriggerColor()));
        container.addView(alphaBar);

        builder.setView(container);
        
        final int[] tempColor = {config.getTriggerColor()};
        
        wheel.setOnColorSelectedListener(color -> {
            // Combine with alpha
            int a = alphaBar.getProgress();
            tempColor[0] = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
            config.setTriggerColor(tempColor[0]); // Update immediately for preview if desired, or wait for OK
            updateColorPreview(tempColor[0]);
            updatePreview();
        });
        
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                 int c = tempColor[0];
                 tempColor[0] = Color.argb(p, Color.red(c), Color.green(c), Color.blue(c));
                 config.setTriggerColor(tempColor[0]);
                 updateColorPreview(tempColor[0]);
                 updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        
        builder.setPositiveButton("OK", null);
        builder.show();
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
    
    private static class AppInfoWrapper {
        ApplicationInfo info;
        String label;
        
        AppInfoWrapper(ApplicationInfo info, PackageManager pm) {
            this.info = info;
            this.label = pm.getApplicationLabel(info).toString();
        }
    }

    private void showAppPicker(@Nullable ShortcutItem editingItem) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppInfoWrapper> validApps = new ArrayList<>();
        
        // Collect existing package names to prevent duplicates
        Set<String> existingPackages = new HashSet<>();
        for (ShortcutItem item : config.getItems()) {
            if (item.getType() == ShortcutItem.TYPE_APP && item.getPackageName() != null) {
                existingPackages.add(item.getPackageName());
            }
        }
        
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                validApps.add(new AppInfoWrapper(app, pm));
            }
        }
        
        Collections.sort(validApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null);
        RecyclerView rv = dialogView.findViewById(R.id.recyclerApps);
        EditText etSearch = dialogView.findViewById(R.id.etSearch);
        
        rv.setLayoutManager(new GridLayoutManager(this, 6));
        
        AppPickerAdapter adapter = new AppPickerAdapter(validApps, editingItem != null, pm, existingPackages);
        rv.setAdapter(adapter);
        
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(editingItem == null ? "Select Apps" : "Change App")
            .setView(dialogView)
            .setPositiveButton(editingItem == null ? "Add Selected" : "Save", null)
            .setNegativeButton("Cancel", null)
            .create();
            
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<AppInfoWrapper> selected = adapter.getSelected();
                
                // Filter out already existing apps just in case, though UI should handle it
                List<AppInfoWrapper> newApps = new ArrayList<>();
                for (AppInfoWrapper app : selected) {
                    if (!existingPackages.contains(app.info.packageName)) {
                         newApps.add(app);
                    }
                }
                
                if (newApps.isEmpty()) {
                    // Check if it was empty because they selected nothing, or only selected duplicates
                    if (selected.isEmpty()) {
                         Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show();
                    } else {
                         // Only selected existing items, just close
                         dialog.dismiss();
                    }
                    return;
                }
                
                dialog.dismiss();
                
                if (editingItem != null) {
                    // Single edit
                    AppInfoWrapper app = newApps.get(0);
                    editingItem.setLabel(app.label);
                    editingItem.setPackageName(app.info.packageName);
                    itemAdapter.notifyDataSetChanged();
                } else {
                    // Multi add
                    processSelectedApps(newApps);
                }
            });
        });
        
        dialog.show();
    }
    
    private void processSelectedApps(List<AppInfoWrapper> apps) {
        String[] options = {"Original Icon", "Colored Block"};
        new AlertDialog.Builder(this)
            .setTitle("Display Mode")
            .setItems(options, (d, which) -> {
                for(AppInfoWrapper app : apps) {
                    ShortcutItem item = new ShortcutItem(java.util.UUID.randomUUID().toString(), ShortcutItem.TYPE_APP, app.label);
                    item.setPackageName(app.info.packageName);
                    item.setDisplayMode(which);
                    if (which == ShortcutItem.MODE_COLOR_BLOCK) {
                        item.setColorInfo(generateUniqueColor());
                    }
                    config.getItems().add(item);
                }
                itemAdapter.notifyDataSetChanged();
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
            startActivityForResult(data, REQUEST_CREATE_SHORTCUT);
        }
        else if (requestCode == REQUEST_CREATE_SHORTCUT && data != null) {
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
    
    private class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.VH> {
        List<AppInfoWrapper> originalList;
        List<AppInfoWrapper> displayList;
        boolean singleSelection;
        PackageManager pm;
        Set<String> existingPackages;
        List<AppInfoWrapper> selected = new ArrayList<>();
        
        AppPickerAdapter(List<AppInfoWrapper> list, boolean singleSelection, PackageManager pm, Set<String> existingPackages) {
            this.originalList = list;
            this.displayList = new ArrayList<>(list);
            this.singleSelection = singleSelection;
            this.pm = pm;
            this.existingPackages = existingPackages != null ? existingPackages : new HashSet<>();
        }
        
        List<AppInfoWrapper> getSelected() { return selected; }
        
        public void filter(String query) {
            displayList.clear();
            if (query.isEmpty()) {
                displayList.addAll(originalList);
            } else {
                String q = query.toLowerCase();
                for (AppInfoWrapper app : originalList) {
                    if (app.label.toLowerCase().contains(q)) {
                        displayList.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }
        
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_selection, parent, false));
        }
        
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            AppInfoWrapper item = displayList.get(position);
            holder.tv.setText(item.label);
            holder.icon.setImageDrawable(item.info.loadIcon(pm));
            
            boolean exists = existingPackages.contains(item.info.packageName);
            boolean isSelected = selected.contains(item);
            
            if (exists) {
                holder.cb.setChecked(true);
                holder.itemView.setAlpha(0.5f);
                holder.itemView.setEnabled(false);
                holder.cb.setEnabled(false);
            } else {
                holder.cb.setChecked(isSelected);
                holder.itemView.setAlpha(1.0f);
                holder.itemView.setEnabled(true);
                holder.cb.setEnabled(false); // Checkbox is visual, click triggers item click
            }
            
            holder.itemView.setOnClickListener(v -> {
                if (exists) return; // Should not happen due to setEnabled(false) but safe guard
                
                if (singleSelection) {
                    selected.clear();
                    selected.add(item);
                    notifyDataSetChanged();
                } else {
                    if (selected.contains(item)) selected.remove(item);
                    else selected.add(item);
                    notifyItemChanged(position);
                }
            });
        }
        
        @Override public int getItemCount() { return displayList.size(); }
        
        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            ImageView icon;
            CheckBox cb;
            VH(View v) {
                super(v);
                tv = v.findViewById(R.id.tvLabel);
                icon = v.findViewById(R.id.imgIcon);
                cb = v.findViewById(R.id.cbSelect);
            }
        }
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