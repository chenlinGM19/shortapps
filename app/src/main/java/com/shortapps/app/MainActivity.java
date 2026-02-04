package com.shortapps.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerWindows;
    private WindowAdapter adapter;
    private List<DataModel.WindowConfig> windows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkOverlayPermission();

        recyclerWindows = findViewById(R.id.recyclerWindows);
        recyclerWindows.setLayoutManager(new LinearLayoutManager(this));
        
        SeekBar seekPillSize = findViewById(R.id.seekPillSize);
        seekPillSize.setProgress(DataManager.getPillSize(this));
        seekPillSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int val = Math.max(30, seekBar.getProgress());
                DataManager.savePillSize(MainActivity.this, val);
                restartService();
            }
        });

        findViewById(R.id.btnNewWindow).setOnClickListener(v -> {
            DataModel.WindowConfig newWindow = new DataModel.WindowConfig("New Window " + (windows.size() + 1));
            windows.add(newWindow);
            DataManager.saveWindows(this, windows);
            loadData();
            // Open editor immediately
            openEditor(newWindow.id);
        });
        
        findViewById(R.id.btnStartService).setOnClickListener(v -> restartService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        windows = DataManager.loadWindows(this);
        adapter = new WindowAdapter(windows);
        recyclerWindows.setAdapter(adapter);
    }
    
    private void openEditor(String id) {
        Intent i = new Intent(this, WindowEditorActivity.class);
        i.putExtra("window_id", id);
        startActivity(i);
    }
    
    private void restartService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        stopService(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Service Updated", Toast.LENGTH_SHORT).show();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Shortapps needs 'Display over other apps' to function.")
                .setPositiveButton("Grant", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
        }
    }

    class WindowAdapter extends RecyclerView.Adapter<WindowAdapter.Holder> {
        List<DataModel.WindowConfig> list;

        public WindowAdapter(List<DataModel.WindowConfig> list) { this.list = list; }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_window_config, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DataModel.WindowConfig item = list.get(position);
            holder.tvName.setText(item.name);
            holder.tvCount.setText(item.items.size() + " Shortcuts");
            holder.swNotif.setChecked(item.showInNotification);
            
            holder.itemView.setOnClickListener(v -> openEditor(item.id));
            
            holder.swNotif.setOnCheckedChangeListener((btn, checked) -> {
                item.showInNotification = checked;
                DataManager.saveWindows(MainActivity.this, list);
                restartService(); // Refresh notification
            });
            
            holder.btnDelete.setOnClickListener(v -> {
                list.remove(position);
                DataManager.saveWindows(MainActivity.this, list);
                notifyItemRemoved(position);
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            SwitchMaterial swNotif;
            View btnDelete;
            public Holder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvCount = itemView.findViewById(R.id.tvCount);
                swNotif = itemView.findViewById(R.id.swNotif);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}