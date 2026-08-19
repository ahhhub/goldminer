package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.MineLayer;
import com.godminer.model.Mineral;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * 矿物管理器 - 根据 layers.yml 维护"方块 -> 挖掘奖励"的映射
 * 生成概率与层级结构由 LayerManager 负责。
 */
public class MineralManager {

    private final GoldMiner plugin;
    private final Map<Material, Mineral> mineralsByMaterial = new HashMap<>();

    public MineralManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    /**
     * 从已加载的层级定义中收集所有方块的奖励。
     * 同一方块出现在多个层级时，以最上层（最先定义）的奖励为准。
     */
    public void loadMinerals() {
        mineralsByMaterial.clear();

        for (MineLayer layer : plugin.getLayerManager().getLayers()) {
            for (MineLayer.WeightedBlock wb : layer.getBaseBlocks()) {
                putIfAbsent(wb.material, wb.coin, wb.exp);
            }
            for (MineLayer.WeightedBlock wb : layer.getOres()) {
                putIfAbsent(wb.material, wb.coin, wb.exp);
            }
        }

        // 基岩不给予奖励（挖不动）
        mineralsByMaterial.putIfAbsent(plugin.getLayerManager().getBedrockMaterial(),
                new Mineral(plugin.getLayerManager().getBedrockMaterial(), 0, 0));

        plugin.getLogger().info("已加载 " + mineralsByMaterial.size() + " 种方块的奖励定义。");
    }

    private void putIfAbsent(Material material, int coin, int exp) {
        mineralsByMaterial.putIfAbsent(material, new Mineral(material, coin, exp));
    }

    /**
     * 查找方块的挖掘奖励（未定义返回 null）
     */
    public Mineral findMineral(Material material) {
        return mineralsByMaterial.get(material);
    }

    public Map<Material, Mineral> getMineralsByMaterial() {
        return mineralsByMaterial;
    }
}
