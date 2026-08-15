package com.godminer.model;

import org.bukkit.Material;

/**
 * 矿物数据模型
 */
public class Mineral {
    private final Material material;
    private final MineralRarity rarity;
    private final double relativeProbability; // 在对应品质中的相对概率
    private final int coinReward;
    private final int expReward;

    public Mineral(Material material, MineralRarity rarity, double relativeProbability, int coinReward, int expReward) {
        this.material = material;
        this.rarity = rarity;
        this.relativeProbability = relativeProbability;
        this.coinReward = coinReward;
        this.expReward = expReward;
    }

    public Material getMaterial() {
        return material;
    }

    public MineralRarity getRarity() {
        return rarity;
    }

    public double getRelativeProbability() {
        return relativeProbability;
    }

    public int getCoinReward() {
        return coinReward;
    }

    public int getExpReward() {
        return expReward;
    }
}
