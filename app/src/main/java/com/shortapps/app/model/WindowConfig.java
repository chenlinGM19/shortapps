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
    private boolean enabledInNotification;

    public WindowConfig(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.columns = 4;
        this.itemSizeDp = 50;
        this.items = new ArrayList<>();
        this.enabledInNotification = true;
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
}