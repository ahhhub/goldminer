package com.godminer.model;

import org.bukkit.Material;

/**
 * 矿物数据模型 - 记录方块挖掉后给予的金币与经验奖励
 * 奖励定义来自 layers.yml 中各层级的方块配置。
 */
public class Mineral {
    private final Material material;
    private final int coinReward;
    private final int expReward;

    public Mineral(Material material, int coinReward, int expReward) {
        this.material = material;
        this.coinReward = coinReward;
        this.expReward = expReward;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCoinReward() {
        return coinReward;
    }

    public int getExpReward() {
        return expReward;
    }
}
