package com.godminer.model;

/**
 * 矿物品质等级
 */
public enum MineralRarity {
    COMMON("普通", "&7"),
    RARE("稀有", "&a"),
    EPIC("史诗", "&5"),
    LEGENDARY("传奇", "&6");

    private final String displayName;
    private final String colorCode;

    MineralRarity(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }
}
