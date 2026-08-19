package com.godminer.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 矿场层级模型
 * 每个层级由基石(base-blocks)与矿石(ores)组成，
 * 生成时按全局权重比例（默认 90% 基石 / 10% 矿石）抽取，
 * 矿石池包含本层矿石以及从上层继承（衰减）下来的矿石。
 */
public class MineLayer {

    /** 层级中的加权方块定义 */
    public static class WeightedBlock {
        public final Material material;
        public final double weight;
        public final int coin;
        public final int exp;
        public final boolean inherit; // 是否允许继承到更深的层级

        public WeightedBlock(Material material, double weight, int coin, int exp, boolean inherit) {
            this.material = material;
            this.weight = Math.max(0.0, weight);
            this.coin = coin;
            this.exp = exp;
            this.inherit = inherit;
        }
    }

    private final String key;
    private final String displayName;
    private final int height;
    private final List<WeightedBlock> baseBlocks = new ArrayList<>();
    private final List<WeightedBlock> ownOres = new ArrayList<>();
    private final List<WeightedBlock> orePool = new ArrayList<>(); // 本层矿石 + 继承矿石

    private double baseTotalWeight;
    private double oreTotalWeight;
    private int yMin; // 含
    private int yMax; // 不含

    public MineLayer(String key, String displayName, int height) {
        this.key = key;
        this.displayName = displayName;
        this.height = Math.max(1, height);
    }

    public void addBaseBlock(WeightedBlock block) {
        baseBlocks.add(block);
        baseTotalWeight += block.weight;
    }

    public void addOre(WeightedBlock block) {
        ownOres.add(block);
        orePool.add(block);
        oreTotalWeight += block.weight;
    }

    /**
     * 构建有效矿石池：本层矿石 + 所有上层矿石（按距离衰减，且 inherit=true 才继承）
     * @param allLayers 全部层级（自上而下）
     * @param myIndex    本层在列表中的下标
     * @param decay      每深一层的衰减系数
     */
    public void buildOrePool(List<MineLayer> allLayers, int myIndex, double decay) {
        for (int j = 0; j < myIndex; j++) {
            MineLayer above = allLayers.get(j);
            double factor = Math.pow(decay, myIndex - j);
            for (WeightedBlock wb : above.ownOres) {
                if (!wb.inherit || wb.weight <= 0) continue;
                double inheritedWeight = wb.weight * factor;
                if (inheritedWeight <= 0.0001) continue;
                orePool.add(new WeightedBlock(wb.material, inheritedWeight, wb.coin, wb.exp, wb.inherit));
                oreTotalWeight += inheritedWeight;
            }
        }
    }

    /**
     * 在该层级抽取一个方块
     * @param basePercent 基石整体占比（0-100）
     */
    public Material roll(ThreadLocalRandom rnd, double basePercent) {
        double r = rnd.nextDouble() * 100.0;
        if (orePool.isEmpty() || r < basePercent) {
            return rollWeighted(baseBlocks, baseTotalWeight, rnd);
        }
        return rollWeighted(orePool, oreTotalWeight, rnd);
    }

    private static Material rollWeighted(List<WeightedBlock> list, double totalWeight, ThreadLocalRandom rnd) {
        if (list.isEmpty() || totalWeight <= 0) return Material.STONE;
        double roll = rnd.nextDouble() * totalWeight;
        double cumulative = 0;
        for (WeightedBlock wb : list) {
            cumulative += wb.weight;
            if (roll <= cumulative) {
                return wb.material;
            }
        }
        return list.get(list.size() - 1).material;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public int getHeight() { return height; }
    public List<WeightedBlock> getBaseBlocks() { return baseBlocks; }
    public List<WeightedBlock> getOres() { return ownOres; }
    public List<WeightedBlock> getOrePool() { return orePool; }

    public int getYMin() { return yMin; }
    public int getYMax() { return yMax; }
    public void setYRange(int yMin, int yMax) {
        this.yMin = yMin;
        this.yMax = yMax;
    }
}
