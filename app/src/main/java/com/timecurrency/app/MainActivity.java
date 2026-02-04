package com.timecurrency.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private TextView tvAmount;
    private TextView tvLabel;
    private MaterialButton btnVibration;
    
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(CurrencyManager.EXTRA_AMOUNT)) {
                int amount = intent.getIntExtra(CurrencyManager.EXTRA_AMOUNT, 0);
                tvAmount.setText(String.valueOf(amount));
                
                if (intent.hasExtra(CurrencyManager.EXTRA_MODE_LABEL)) {
                    tvLabel.setText(intent.getStringExtra(CurrencyManager.EXTRA_MODE_LABEL));
                }
            } else {
                refreshUI();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Edge to Edge Mode
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        tvAmount = findViewById(R.id.tvAmount);
        tvLabel = findViewById(R.id.tvLabel);
        View btnAdd = findViewById(R.id.btnAdd);
        View btnMinus = findViewById(R.id.btnMinus);
        View btnHistory = findViewById(R.id.btnHistory);
        View btnIconConfig = findViewById(R.id.btnIconConfig);
        
        // Setup Mode Toggle
        View cardCounter = findViewById(R.id.cardCounter);
        cardCounter.setOnClickListener(v -> {
            CurrencyManager.toggleMode(this);
            refreshUI();
        });

        btnAdd.setOnClickListener(v -> updateCurrencyOptimistic(1));
        btnMinus.setOnClickListener(v -> updateCurrencyOptimistic(-1));
        
        // History Button
        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });
        
        // Icon Config Button
        btnIconConfig.setOnClickListener(v -> {
            startActivity(new Intent(this, IconConfigActivity.class));
        });

        // Vibration Setting Button
        btnVibration = findViewById(R.id.btnVibration);
        btnVibration.setOnClickListener(v -> {
            int newLevel = CurrencyManager.cycleVibration(this);
            updateVibrationIcon(newLevel);
        });

        checkPermissions();
        // Only start service if we have permission. 
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startForegroundServiceSafe();
        }
        
        refreshUI();
    }
    
    /**
     * Intercept hardware volume keys to control currency.
     * Returns true to prevent system volume change.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                updateCurrencyOptimistic(1);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                updateCurrencyOptimistic(-1);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void updateCurrencyOptimistic(int delta) {
        try {
            String currentStr = tvAmount.getText().toString();
            int current = Integer.parseInt(currentStr);
            tvAmount.setText(String.valueOf(current + delta));
        } catch (Exception e) {
            // Ignore
        }
        CurrencyManager.updateCurrency(this, delta);
    }
    
    private void updateVibrationIcon(int level) {
        String text = "VIB: OFF";
        
        switch (level) {
            case CurrencyManager.VIB_OFF:
                text = "VIB: OFF";
                btnVibration.setAlpha(0.5f);
                break;
            case CurrencyManager.VIB_LIGHT:
                text = "VIB: LOW";
                btnVibration.setAlpha(1.0f);
                break;
            case CurrencyManager.VIB_HEAVY:
                text = "VIB: HIGH";
                btnVibration.setAlpha(1.0f);
                break;
        }
        btnVibration.setText(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(CurrencyManager.ACTION_UPDATE_UI);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
        refreshUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void refreshUI() {
        int amount = CurrencyManager.getCurrency(this);
        tvAmount.setText(String.valueOf(amount));
        tvLabel.setText(CurrencyManager.getModeLabel(this));
        
        updateVibrationIcon(CurrencyManager.getVibrationLevel(this));
    }

    private void startForegroundServiceSafe() {
        try {
            Intent serviceIntent = new Intent(this, NotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startForegroundServiceSafe();
        }
    }
}