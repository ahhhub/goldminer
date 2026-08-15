package com.godminer.model;

import org.bukkit.Material;
import java.util.*;

/**
 * 镐子等级
 */
public enum PickaxeTier {
    WOOD(0, "wood", Material.WOODEN_PICKAXE),
    STONE(1, "stone", Material.STONE_PICKAXE),
    COPPER(2, "copper", Material.COPPER_PICKAXE),
    GOLD(3, "gold", Material.GOLDEN_PICKAXE),
    IRON(4, "iron", Material.IRON_PICKAXE),
    DIAMOND(5, "diamond", Material.DIAMOND_PICKAXE),
    NETHERITE(6, "netherite", Material.NETHERITE_PICKAXE);

    private final int order;
    private final String configKey;
    private final Material baseMaterial;

    PickaxeTier(int order, String configKey, Material baseMaterial) {
        this.order = order;
        this.configKey = configKey;
        this.baseMaterial = baseMaterial;
    }

    public int getOrder() {
        return order;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Material getBaseMaterial() {
        return baseMaterial;
    }

    public PickaxeTier next() {
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal < values().length) {
            return values()[nextOrdinal];
        }
        return this; // 已经是最高等级
    }

    /**
     * 铁镐及以上才有扩展的附魔上限
     */
    public boolean hasExtendedEnchantLimits() {
        return this.ordinal() >= IRON.ordinal();
    }

    /**
     * 钻石镐及以上有更高的附魔上限
     */
    public boolean hasDiamondEnchantLimits() {
        return this.ordinal() >= DIAMOND.ordinal();
    }

    /**
     * 下界合金镐有最高附魔上限
     */
    public boolean hasNetheriteEnchantLimits() {
        return this == NETHERITE;
    }
}
