package com.timecurrency.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecyclerView.Adapter currentAdapter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    // For Exporting
    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    exportDataToUri(uri);
                }
            }
    );
    
    // For Importing
    private final ActivityResultLauncher<String[]> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importDataFromUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_history);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggleViewMode);
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnModeDaily) {
                    loadDailyData();
                } else {
                    loadTransactionData();
                }
            }
        });
        
        loadTransactionData();

        findViewById(R.id.btnExport).setOnClickListener(v -> {
            String fileName = "time_currency_export_" + System.currentTimeMillis() + ".json";
            createDocumentLauncher.launch(fileName);
        });
        
        findViewById(R.id.btnImport).setOnClickListener(v -> {
             openDocumentLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
        });
    }

    private void loadTransactionData() {
        List<TransactionItem> items = new ArrayList<>();
        try (Cursor cursor = TransactionDbHelper.getAllTransactions(this)) {
            while (cursor.moveToNext()) {
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(TransactionDbHelper.COLUMN_TIMESTAMP));
                int delta = cursor.getInt(cursor.getColumnIndexOrThrow(TransactionDbHelper.COLUMN_DELTA));
                items.add(new TransactionItem(timestamp, delta));
            }
        }
        currentAdapter = new TransactionAdapter(items);
        recyclerView.setAdapter(currentAdapter);
    }
    
    private void loadDailyData() {
        List<TransactionDbHelper.DailySummary> summaries = TransactionDbHelper.getDailySummaries(this);
        currentAdapter = new DailyAdapter(summaries);
        recyclerView.setAdapter(currentAdapter);
    }
    
    private void exportDataToUri(android.net.Uri uri) {
        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray();
                try (Cursor cursor = TransactionDbHelper.getAllTransactions(this)) {
                    while (cursor.moveToNext()) {
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(TransactionDbHelper.COLUMN_TIMESTAMP));
                        int delta = cursor.getInt(cursor.getColumnIndexOrThrow(TransactionDbHelper.COLUMN_DELTA));
                        int totalSnapshot = cursor.getInt(cursor.getColumnIndexOrThrow(TransactionDbHelper.COLUMN_TOTAL_SNAPSHOT));
                        
                        JSONObject obj = new JSONObject();
                        obj.put("timestamp", timestamp);
                        obj.put("dateTime", dateFormat.format(new Date(timestamp)));
                        obj.put("delta", delta);
                        obj.put("totalSnapshot", totalSnapshot);
                        jsonArray.put(obj);
                    }
                }
                
                String jsonString = jsonArray.toString(4); // Indent 4 spaces
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(jsonString.getBytes());
                }
                
                runOnUiThread(() -> Toast.makeText(this, "Export Successful", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void importDataFromUri(Uri uri) {
        new Thread(() -> {
            try {
                StringBuilder stringBuilder = new StringBuilder();
                try (InputStream inputStream = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                }
                
                String jsonString = stringBuilder.toString();
                JSONArray jsonArray = new JSONArray(jsonString);
                
                int importedCount = 0;
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    long timestamp = obj.getLong("timestamp");
                    int delta = obj.getInt("delta");
                    
                    if (TransactionDbHelper.importTransaction(this, timestamp, delta)) {
                        importedCount++;
                    }
                }
                
                // Recalculate totals
                CurrencyManager.recalculateTotals(this);
                
                final int count = importedCount;
                runOnUiThread(() -> {
                    Toast.makeText(this, "Imported " + count + " new records", Toast.LENGTH_SHORT).show();
                    
                    // Refresh current view
                    MaterialButtonToggleGroup toggle = findViewById(R.id.toggleViewMode);
                    if (toggle.getCheckedButtonId() == R.id.btnModeDaily) loadDailyData();
                    else loadTransactionData();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // --- Transaction List Adapter ---

    private static class TransactionItem {
        long timestamp;
        int delta;

        TransactionItem(long timestamp, int delta) {
            this.timestamp = timestamp;
            this.delta = delta;
        }
    }

    private class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
        private final List<TransactionItem> list;

        TransactionAdapter(List<TransactionItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionItem item = list.get(position);
            holder.tvDate.setText(dateFormat.format(new Date(item.timestamp)));
            String sign = item.delta > 0 ? "+" : "";
            holder.tvDelta.setText(sign + item.delta);
            holder.tvDelta.setTextColor(item.delta > 0 ? 
                    getColor(R.color.md_theme_dark_primary) : getColor(R.color.md_theme_dark_error));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvDelta;

            ViewHolder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvDelta = itemView.findViewById(R.id.tvDelta);
            }
        }
    }
    
    // --- Daily Summary Adapter ---
    
    private class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.DailyViewHolder> {
        private final List<TransactionDbHelper.DailySummary> list;

        DailyAdapter(List<TransactionDbHelper.DailySummary> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public DailyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_daily_summary, parent, false);
            return new DailyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DailyViewHolder holder, int position) {
            TransactionDbHelper.DailySummary item = list.get(position);
            holder.tvDay.setText(item.dateStr);
            String sign = item.totalChange > 0 ? "+" : "";
            holder.tvTotal.setText(sign + item.totalChange);
            holder.tvTotal.setTextColor(item.totalChange >= 0 ? 
                    getColor(R.color.md_theme_dark_primary) : getColor(R.color.md_theme_dark_error));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class DailyViewHolder extends RecyclerView.ViewHolder {
            TextView tvDay, tvTotal;

            DailyViewHolder(View itemView) {
                super(itemView);
                tvDay = itemView.findViewById(R.id.tvDay);
                tvTotal = itemView.findViewById(R.id.tvDailyTotal);
            }
        }
    }
}