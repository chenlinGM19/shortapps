package com.shortapps.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rv;
    private List<WindowConfig> configs;
    private WindowAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        checkPermissions();

        rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> {
            WindowConfig newConfig = new WindowConfig("New Window " + (configs.size() + 1));
            configs.add(newConfig);
            save();
            adapter.notifyItemInserted(configs.size() - 1);
        });
        
        startService();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
    
    private void loadData() {
        configs = ConfigManager.loadWindows(this);
        adapter = new WindowAdapter();
        rv.setAdapter(adapter);
    }
    
    private void save() {
        ConfigManager.saveWindows(this, configs);
        // Restart service to pick up changes
        startService();
    }
    
    private void startService() {
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 101);
        }
    }
    
    class WindowAdapter extends RecyclerView.Adapter<WindowAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_window_config, parent, false);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            WindowConfig c = configs.get(position);
            holder.tvName.setText(c.getName());
            holder.tvInfo.setText(c.getItems().size() + " Items | " + c.getColumns() + " Cols");
            
            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, EditorActivity.class);
                i.putExtra("window_index", position);
                startActivity(i);
            });
            
            holder.itemView.setOnLongClickListener(v -> {
                configs.remove(position);
                save();
                notifyItemRemoved(position);
                return true;
            });
        }

        @Override public int getItemCount() { return configs.size(); }
        
        class Holder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo;
            Holder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvName);
                tvInfo = v.findViewById(R.id.tvInfo);
            }
        }
    }
}