package com.shortapps.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WindowEditorActivity extends AppCompatActivity {

    private DataModel.WindowConfig windowConfig;
    private ShortcutAdapter adapter;
    private List<DataModel.WindowConfig> allWindows;
    private EditText etName, etColumns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_window_editor);

        String id = getIntent().getStringExtra("window_id");
        allWindows = DataManager.loadWindows(this);
        for (DataModel.WindowConfig w : allWindows) {
            if (w.id.equals(id)) {
                windowConfig = w;
                break;
            }
        }

        if (windowConfig == null) {
            finish();
            return;
        }

        etName = findViewById(R.id.etWindowName);
        etColumns = findViewById(R.id.etColumns);
        etName.setText(windowConfig.name);
        etColumns.setText(String.valueOf(windowConfig.columns));

        RecyclerView recycler = findViewById(R.id.recyclerShortcuts);
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ShortcutAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.btnAddApp).setOnClickListener(v -> showAppPicker());
        findViewById(R.id.btnAddTasker).setOnClickListener(v -> showTaskerDialog());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndExit());
    }

    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<ApplicationInfo> userApps = new ArrayList<>();
        
        String[] appNames = new String[apps.size()];
        // Simple filter for launchable apps could be better, but keeping it simple
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                userApps.add(app);
            }
        }
        
        String[] names = new String[userApps.size()];
        for(int i=0; i<userApps.size(); i++) names[i] = userApps.get(i).loadLabel(pm).toString();

        new AlertDialog.Builder(this)
                .setTitle("Select App")
                .setItems(names, (dialog, which) -> {
                    ApplicationInfo selected = userApps.get(which);
                    DataModel.ShortcutItem item = new DataModel.ShortcutItem(
                            DataModel.ShortcutItem.Type.APP,
                            selected.loadLabel(pm).toString(),
                            selected.packageName
                    );
                    item.blockColor = generateRandomPastel();
                    windowConfig.items.add(item);
                    adapter.notifyDataSetChanged();
                })
                .show();
    }
    
    private void showTaskerDialog() {
        final EditText input = new EditText(this);
        input.setHint("Task Name");
        new AlertDialog.Builder(this)
                .setTitle("Add Tasker Task")
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    String task = input.getText().toString();
                    if (!TextUtils.isEmpty(task)) {
                        DataModel.ShortcutItem item = new DataModel.ShortcutItem(
                                DataModel.ShortcutItem.Type.TASKER,
                                task,
                                task
                        );
                        item.blockColor = generateRandomPastel();
                        // Tasker always uses Block mode in this logic (or icon if available, but we default to block for tasks usually)
                        item.displayMode = DataModel.ShortcutItem.DisplayMode.BLOCK;
                        windowConfig.items.add(item);
                        adapter.notifyDataSetChanged();
                    }
                })
                .show();
    }
    
    private String generateRandomPastel() {
        Random r = new Random();
        int red = (r.nextInt(127) + 127);
        int green = (r.nextInt(127) + 127);
        int blue = (r.nextInt(127) + 127);
        return String.format("#%02x%02x%02x", red, green, blue);
    }

    private void saveAndExit() {
        windowConfig.name = etName.getText().toString();
        try {
            windowConfig.columns = Integer.parseInt(etColumns.getText().toString());
        } catch (Exception e) {}
        
        DataManager.saveWindows(this, allWindows);
        finish();
    }

    class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_editor_shortcut, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DataModel.ShortcutItem item = windowConfig.items.get(position);
            holder.tvLabel.setText(item.label);
            
            if (item.displayMode == DataModel.ShortcutItem.DisplayMode.ICON) {
                holder.tvMode.setText("Mode: Icon");
            } else {
                holder.tvMode.setText("Mode: Block");
            }
            
            holder.tvMode.setOnClickListener(v -> {
                // Toggle mode
                if (item.displayMode == DataModel.ShortcutItem.DisplayMode.ICON) {
                    item.displayMode = DataModel.ShortcutItem.DisplayMode.BLOCK;
                } else {
                    item.displayMode = DataModel.ShortcutItem.DisplayMode.ICON;
                }
                notifyItemChanged(position);
            });
            
            holder.btnDelete.setOnClickListener(v -> {
                windowConfig.items.remove(position);
                notifyItemRemoved(position);
            });
        }

        @Override public int getItemCount() { return windowConfig.items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvMode;
            ImageView btnDelete;
            public Holder(@NonNull View itemView) {
                super(itemView);
                tvLabel = itemView.findViewById(R.id.tvLabel);
                tvMode = itemView.findViewById(R.id.tvMode);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}