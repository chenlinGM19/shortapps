package com.shortapps.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataModel {

    public static class WindowConfig {
        public String id;
        public String name;
        public int columns = 4;
        public boolean showInNotification = false;
        public List<ShortcutItem> items = new ArrayList<>();

        public WindowConfig(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
        }
    }

    public static class ShortcutItem {
        public enum Type { APP, TASKER }
        public enum DisplayMode { ICON, BLOCK }

        public String id;
        public Type type;
        public String label;
        public String packageName; // For Apps
        public String taskName; // For Tasker
        public DisplayMode displayMode = DisplayMode.ICON;
        public String blockColor; // Hex code

        public ShortcutItem(Type type, String label, String value) {
            this.id = UUID.randomUUID().toString();
            this.type = type;
            this.label = label;
            if (type == Type.APP) this.packageName = value;
            else this.taskName = value;
        }
    }
}