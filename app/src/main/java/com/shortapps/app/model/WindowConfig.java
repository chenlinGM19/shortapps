package com.shortapps.app.model;

import android.graphics.Color;
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
    
    // Window Settings
    private boolean showLabels;
    
    // Trigger Button Settings
    private boolean triggerEnabled;
    private boolean cornerSnap; // New field for Corner Snapping
    private int cornerAnchor;   // 0:TL, 1:TR, 2:BL, 3:BR, -1:Unset
    private int triggerWidth;  // dp
    private int triggerHeight; // dp
    
    // Individual Corner Radii (dp)
    private int radiusTL; 
    private int radiusTR;
    private int radiusBL;
    private int radiusBR;
    
    private int triggerColor;  // ARGB
    private int triggerStyle;  // 0: Solid, 1: Outline, 2: Glass, 3: Inverted
    private int triggerX;
    private int triggerY;

    // Legacy support fields
    private int triggerSize; 
    private int triggerRadius; // Kept for backward compatibility migration

    public WindowConfig(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.columns = 4;
        this.itemSizeDp = 50;
        this.items = new ArrayList<>();
        
        // Defaults
        this.enabledInNotification = true;
        this.showLabels = true;
        this.triggerEnabled = true;
        this.cornerSnap = false; 
        this.cornerAnchor = -1; // Default unset
        this.triggerWidth = 60;
        this.triggerHeight = 60;
        
        // Default rounded corners (pill shape logic handled in getters if needed, but defaults to 30)
        this.radiusTL = 30;
        this.radiusTR = 30;
        this.radiusBL = 30;
        this.radiusBR = 30;
        
        this.triggerColor = Color.parseColor("#99000000"); // Transparent Black
        this.triggerStyle = 0;
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
    
    public boolean isShowLabels() { return showLabels; }
    public void setShowLabels(boolean showLabels) { this.showLabels = showLabels; }
    
    public boolean isTriggerEnabled() { return triggerEnabled; }
    public void setTriggerEnabled(boolean triggerEnabled) { this.triggerEnabled = triggerEnabled; }
    
    public boolean isCornerSnap() { return cornerSnap; }
    public void setCornerSnap(boolean cornerSnap) { this.cornerSnap = cornerSnap; }

    public int getCornerAnchor() { return cornerAnchor; }
    public void setCornerAnchor(int cornerAnchor) { this.cornerAnchor = cornerAnchor; }
    
    public int getTriggerWidth() { 
        return triggerWidth > 0 ? triggerWidth : (triggerSize > 0 ? triggerSize : 60); 
    }
    public void setTriggerWidth(int triggerWidth) { this.triggerWidth = triggerWidth; }

    public int getTriggerHeight() { 
        return triggerHeight > 0 ? triggerHeight : (triggerSize > 0 ? triggerSize : 60); 
    }
    public void setTriggerHeight(int triggerHeight) { this.triggerHeight = triggerHeight; }
    
    // Radius Getters/Setters
    public int getRadiusTL() { return radiusTL != 0 ? radiusTL : triggerRadius; }
    public void setRadiusTL(int r) { this.radiusTL = r; }

    public int getRadiusTR() { return radiusTR != 0 ? radiusTR : triggerRadius; }
    public void setRadiusTR(int r) { this.radiusTR = r; }

    public int getRadiusBL() { return radiusBL != 0 ? radiusBL : triggerRadius; }
    public void setRadiusBL(int r) { this.radiusBL = r; }

    public int getRadiusBR() { return radiusBR != 0 ? radiusBR : triggerRadius; }
    public void setRadiusBR(int r) { this.radiusBR = r; }

    // Legacy getter for bulk setting if needed, primarily used for migration
    public int getTriggerRadius() { return triggerRadius; }
    public void setTriggerRadius(int triggerRadius) { 
        this.triggerRadius = triggerRadius;
        this.radiusTL = triggerRadius;
        this.radiusTR = triggerRadius;
        this.radiusBL = triggerRadius;
        this.radiusBR = triggerRadius;
    }

    public int getTriggerColor() { 
        return triggerColor != 0 ? triggerColor : Color.parseColor("#99000000");
    }
    public void setTriggerColor(int triggerColor) { this.triggerColor = triggerColor; }
    
    public int getTriggerStyle() { return triggerStyle; }
    public void setTriggerStyle(int triggerStyle) { this.triggerStyle = triggerStyle; }
    
    public int getTriggerX() { return triggerX; }
    public void setTriggerX(int triggerX) { this.triggerX = triggerX; }
    
    public int getTriggerY() { return triggerY; }
    public void setTriggerY(int triggerY) { this.triggerY = triggerY; }
}