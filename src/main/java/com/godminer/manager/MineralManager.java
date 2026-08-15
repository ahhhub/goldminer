package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.Mineral;
import com.godminer.model.MineralRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 矿物管理器 - 管理所有矿物定义和生成概率
 */
public class MineralManager {

    private final GoldMiner plugin;
    private final Map<MineralRarity, List<Mineral>> mineralsByRarity;
    private final Map<MineralRarity, Double> rarityProbabilities;
    private final Map<MineralRarity, Double> rarityCumulativeProbabilities;
    private double totalRarityProbability;

    public MineralManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.mineralsByRarity = new EnumMap<>(MineralRarity.class);
        this.rarityProbabilities = new EnumMap<>(MineralRarity.class);
        this.rarityCumulativeProbabilities = new EnumMap<>(MineralRarity.class);

        for (MineralRarity rarity : MineralRarity.values()) {
            mineralsByRarity.put(rarity, new ArrayList<>());
        }
    }

    public void loadMinerals() {
        // 清空
        for (List<Mineral> list : mineralsByRarity.values()) {
            list.clear();
        }
        rarityProbabilities.clear();
        rarityCumulativeProbabilities.clear();
        totalRarityProbability = 0;

        // 从 block.yml 加载
        var blockConfig = plugin.getBlockConfig();

        // 加载品质概率 (从 config.yml)
        var raritySection = plugin.getConfig().getConfigurationSection("mineral-rarity-probability");
        if (raritySection != null) {
            for (MineralRarity rarity : MineralRarity.values()) {
                double prob = raritySection.getDouble(rarity.name().toLowerCase(), 0);
                rarityProbabilities.put(rarity, prob);
                totalRarityProbability += prob;
            }
        }

        // 计算累积概率
        double cumulative = 0;
        for (MineralRarity rarity : MineralRarity.values()) {
            cumulative += rarityProbabilities.getOrDefault(rarity, 0.0);
            rarityCumulativeProbabilities.put(rarity, cumulative);
        }

        // 加载各品质的矿物
        for (MineralRarity rarity : MineralRarity.values()) {
            ConfigurationSection raritySection2 = blockConfig.getConfigurationSection(rarity.name().toLowerCase());
            if (raritySection2 == null) continue;

            for (String key : raritySection2.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    double prob = raritySection2.getDouble(key + ".probability", 1.0);
                    int coin = raritySection2.getInt(key + ".coin", 1);
                    int exp = raritySection2.getInt(key + ".exp", 1);
                    Mineral mineral = new Mineral(material, rarity, prob, coin, exp);
                    mineralsByRarity.get(rarity).add(mineral);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的矿物类型: " + key + "，已跳过。");
                }
            }

            // 按概率排序
            mineralsByRarity.get(rarity).sort((a, b) ->
                    Double.compare(b.getRelativeProbability(), a.getRelativeProbability()));
        }

        plugin.getLogger().info("已加载 " + getTotalMineralCount() + " 种矿物定义。");
    }

    private int getTotalMineralCount() {
        return mineralsByRarity.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 随机选择一个矿物品质
     */
    public MineralRarity rollRarity() {
        if (totalRarityProbability <= 0) return MineralRarity.COMMON;

        double roll = ThreadLocalRandom.current().nextDouble() * totalRarityProbability;
        for (MineralRarity rarity : MineralRarity.values()) {
            double cumProb = rarityCumulativeProbabilities.getOrDefault(rarity, 0.0);
            if (roll <= cumProb) {
                return rarity;
            }
        }
        return MineralRarity.COMMON;
    }

    /**
     * 在给定品质中随机选择一个矿物
     */
    public Mineral rollMineral(MineralRarity rarity) {
        List<Mineral> minerals = mineralsByRarity.get(rarity);
        if (minerals.isEmpty()) return null;

        double totalProb = minerals.stream().mapToDouble(Mineral::getRelativeProbability).sum();
        if (totalProb <= 0) return minerals.get(0);

        double roll = ThreadLocalRandom.current().nextDouble() * totalProb;
        double cumulative = 0;
        for (Mineral mineral : minerals) {
            cumulative += mineral.getRelativeProbability();
            if (roll <= cumulative) {
                return mineral;
            }
        }
        return minerals.get(minerals.size() - 1);
    }

    /**
     * 随机生成一个矿物（先选品质再选矿物）
     */
    public Mineral rollRandomMineral() {
        MineralRarity rarity = rollRarity();
        return rollMineral(rarity);
    }

    public List<Mineral> getMineralsByRarity(MineralRarity rarity) {
        return mineralsByRarity.getOrDefault(rarity, Collections.emptyList());
    }

    public Map<MineralRarity, Double> getRarityProbabilities() {
        return Collections.unmodifiableMap(rarityProbabilities);
    }
}
