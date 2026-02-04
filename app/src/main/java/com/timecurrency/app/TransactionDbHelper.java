package com.timecurrency.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Collections;

public class TransactionDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "TimeCurrency.db";

    public static final String TABLE_NAME = "transactions";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_DELTA = "delta";
    public static final String COLUMN_TOTAL_SNAPSHOT = "total_snapshot";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY," +
                    COLUMN_TIMESTAMP + " INTEGER," +
                    COLUMN_DELTA + " INTEGER," +
                    COLUMN_TOTAL_SNAPSHOT + " INTEGER)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    public TransactionDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }
    
    public static void logTransaction(Context context, int delta, int newTotal) {
        try (TransactionDbHelper dbHelper = new TransactionDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            long currentTime = System.currentTimeMillis();
            
            // 1. Check the most recent transaction
            Cursor cursor = db.query(TABLE_NAME, 
                new String[]{COLUMN_ID, COLUMN_TIMESTAMP, COLUMN_DELTA}, 
                null, null, null, null, 
                COLUMN_TIMESTAMP + " DESC", "1");
                
            boolean merged = false;
            
            if (cursor.moveToFirst()) {
                long lastId = cursor.getLong(0);
                long lastTime = cursor.getLong(1);
                int lastDelta = cursor.getInt(2);
                
                // 30 seconds threshold
                if ((currentTime - lastTime) < 30000) {
                    // Update existing record
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_DELTA, lastDelta + delta);
                    values.put(COLUMN_TOTAL_SNAPSHOT, newTotal);
                    values.put(COLUMN_TIMESTAMP, currentTime); // Update time to keep session alive
                    
                    db.update(TABLE_NAME, values, COLUMN_ID + " = ?", new String[]{String.valueOf(lastId)});
                    merged = true;
                }
            }
            cursor.close();
            
            if (!merged) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_TIMESTAMP, currentTime);
                values.put(COLUMN_DELTA, delta);
                values.put(COLUMN_TOTAL_SNAPSHOT, newTotal);
                db.insert(TABLE_NAME, null, values);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static boolean importTransaction(Context context, long timestamp, int delta) {
        try (TransactionDbHelper dbHelper = new TransactionDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            // Check for duplicate based on timestamp
            Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_ID}, 
                    COLUMN_TIMESTAMP + " = ?", 
                    new String[]{String.valueOf(timestamp)}, null, null, null);
            
            boolean exists = cursor.getCount() > 0;
            cursor.close();
            
            if (!exists) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_TIMESTAMP, timestamp);
                values.put(COLUMN_DELTA, delta);
                values.put(COLUMN_TOTAL_SNAPSHOT, 0); 
                db.insert(TABLE_NAME, null, values);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public static int calculateTotalBalance(Context context) {
        int total = 0;
        try (TransactionDbHelper dbHelper = new TransactionDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_DELTA + ") FROM " + TABLE_NAME, null);
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0);
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return total;
    }
    
    public static int calculateDailyBalance(Context context, long startTimeMillis) {
        int total = 0;
        try (TransactionDbHelper dbHelper = new TransactionDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_DELTA + ") FROM " + TABLE_NAME + 
                    " WHERE " + COLUMN_TIMESTAMP + " >= ?", new String[]{String.valueOf(startTimeMillis)});
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0);
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return total;
    }
    
    public static Cursor getAllTransactions(Context context) {
        TransactionDbHelper dbHelper = new TransactionDbHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.query(TABLE_NAME, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
    }
    
    // Structure for Daily Summary
    public static class DailySummary {
        public String dateStr;
        public int totalChange;
        
        public DailySummary(String dateStr, int totalChange) {
            this.dateStr = dateStr;
            this.totalChange = totalChange;
        }
    }
    
    public static List<DailySummary> getDailySummaries(Context context) {
        Map<String, Integer> dailyMap = new TreeMap<>(Collections.reverseOrder()); // Sort Descending
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        try (TransactionDbHelper dbHelper = new TransactionDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_TIMESTAMP, COLUMN_DELTA}, null, null, null, null, null);
            
            while (cursor.moveToNext()) {
                long ts = cursor.getLong(0);
                int delta = cursor.getInt(1);
                
                String day = fmt.format(new Date(ts));
                
                if (dailyMap.containsKey(day)) {
                    dailyMap.put(day, dailyMap.get(day) + delta);
                } else {
                    dailyMap.put(day, delta);
                }
            }
            cursor.close();
        }
        
        List<DailySummary> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dailyMap.entrySet()) {
            results.add(new DailySummary(entry.getKey(), entry.getValue()));
        }
        return results;
    }
}