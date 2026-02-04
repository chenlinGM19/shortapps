package com.shortapps.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WindowConfig {
    private String id;
    private String name;
    private int columns;
    private int itemSizeDp;
    private List<ShortcutItem> items;
    
    // Notification Settings
    private boolean enabledInNotification;
    
    // Trigger Button Settings
    private boolean triggerEnabled;
    private int triggerSize; // dp
    private int triggerRadius; // dp
    private int triggerX;
    private int triggerY;

    public WindowConfig(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.columns = 4;
        this.itemSizeDp = 50;
        this.items = new ArrayList<>();
        
        // Defaults
        this.enabledInNotification = true;
        this.triggerEnabled = true;
        this.triggerSize = 60;
        this.triggerRadius = 30; // Circle by default
        this.triggerX = 0;
        this.triggerY = 300;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }
    
    public int getItemSizeDp() { return itemSizeDp; }
    public void setItemSizeDp(int itemSizeDp) { this.itemSizeDp = itemSizeDp; }
    
    public List<ShortcutItem> getItems() { return items; }
    public void setItems(List<ShortcutItem> items) { this.items = items; }
    
    public boolean isEnabledInNotification() { return enabledInNotification; }
    public void setEnabledInNotification(boolean enabledInNotification) { this.enabledInNotification = enabledInNotification; }
    
    public boolean isTriggerEnabled() { return triggerEnabled; }
    public void setTriggerEnabled(boolean triggerEnabled) { this.triggerEnabled = triggerEnabled; }
    
    public int getTriggerSize() { return triggerSize; }
    public void setTriggerSize(int triggerSize) { this.triggerSize = triggerSize; }
    
    public int getTriggerRadius() { return triggerRadius; }
    public void setTriggerRadius(int triggerRadius) { this.triggerRadius = triggerRadius; }
    
    public int getTriggerX() { return triggerX; }
    public void setTriggerX(int triggerX) { this.triggerX = triggerX; }
    
    public int getTriggerY() { return triggerY; }
    public void setTriggerY(int triggerY) { this.triggerY = triggerY; }
}