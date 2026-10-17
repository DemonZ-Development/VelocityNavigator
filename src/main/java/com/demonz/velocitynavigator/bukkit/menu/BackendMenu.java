/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;

import com.demonz.velocitynavigator.common.MenuBridgeProtocol;

import java.util.ArrayList;
import java.util.List;

public final class BackendMenu {

    private final String name;
    private String title;
    private int rows;
    private String uiType = "inherit";
    private final List<MenuBridgeProtocol.MenuItem> items;

    public BackendMenu(String name, String title, int rows, List<MenuBridgeProtocol.MenuItem> items) {
        this.name = name;
        this.title = title;
        this.rows = Math.max(2, Math.min(6, rows));
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getUiType() { return uiType; }
    public void setUiType(String uiType) { this.uiType = uiType == null ? "inherit" : uiType; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(2, Math.min(6, rows));
    }

    public List<MenuBridgeProtocol.MenuItem> getItems() {
        return new ArrayList<>(items);
    }

    public void setItems(List<MenuBridgeProtocol.MenuItem> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
    }

    private String previousButton;
    private String nextButton;
    private String refreshButton;
    private String closeButton;

    public String getPreviousButton() {
        return previousButton;
    }

    public void setPreviousButton(String previousButton) {
        this.previousButton = previousButton;
    }

    public String getNextButton() {
        return nextButton;
    }

    public void setNextButton(String nextButton) {
        this.nextButton = nextButton;
    }

    public String getRefreshButton() {
        return refreshButton;
    }

    public void setRefreshButton(String refreshButton) {
        this.refreshButton = refreshButton;
    }

    public String getCloseButton() {
        return closeButton;
    }

    public void setCloseButton(String closeButton) {
        this.closeButton = closeButton;
    }
}
