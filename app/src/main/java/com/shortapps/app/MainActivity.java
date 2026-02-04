package com.shortapps.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shortapps.app.model.WindowConfig;
import com.shortapps.app.utils.ConfigManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EXPORT = 200;
    private static final int REQUEST_CODE_IMPORT = 201;

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
        
        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(this::showPopupMenu);
        
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

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "Export Backup");
        popup.getMenu().add(0, 2, 0, "Import Backup");
        
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                exportData();
                return true;
            } else if (item.getItemId() == 2) {
                importData();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void exportData() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "shortapps_backup.json");
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        if (requestCode == REQUEST_CODE_EXPORT) {
            performExport(data.getData());
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            performImport(data.getData());
        }
    }

    private void performExport(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) return;
            
            String json = new Gson().toJson(configs);
            os.write(json.getBytes());
            os.close();
            
            Toast.makeText(this, "Export Successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            is.close();
            
            Type type = new TypeToken<List<WindowConfig>>(){}.getType();
            List<WindowConfig> imported = new Gson().fromJson(sb.toString(), type);
            
            if (imported != null) {
                configs.clear();
                configs.addAll(imported);
                save();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Import Successful", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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