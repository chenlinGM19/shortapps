package com.shortapps.app.model;

public class ShortcutItem {
    public static final int TYPE_APP = 0;
    public static final int TYPE_TASKER = 1;

    public static final int MODE_ICON = 0;
    public static final int MODE_COLOR_BLOCK = 1;

    private String id;
    private int type;
    private String label;
    private String packageName; // For apps
    private String intentUri;   // For generic intents
    private String taskerTaskName; // For Tasker
    private int displayMode;
    private int colorInfo; // Store color integer for MODE_COLOR_BLOCK

    public ShortcutItem(String id, int type, String label) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.displayMode = MODE_ICON;
    }

    // Getters and Setters
    public String getId() { return id; }
    public int getType() { return type; }
    public String getLabel() { return label; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getTaskerTaskName() { return taskerTaskName; }
    public void setTaskerTaskName(String taskerTaskName) { this.taskerTaskName = taskerTaskName; }
    public int getDisplayMode() { return displayMode; }
    public void setDisplayMode(int displayMode) { this.displayMode = displayMode; }
    public int getColorInfo() { return colorInfo; }
    public void setColorInfo(int colorInfo) { this.colorInfo = colorInfo; }
}