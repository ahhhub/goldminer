package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.MineLayer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 分层管理器 - 从 layers.yml 加载矿场的层级定义
 * layers.yml 中 layers 的书写顺序 = 矿场中从上到下的顺序，
 * 最底部自动附加基岩层（默认 1 格）。
 */
public class LayerManager {

    private final GoldMiner plugin;
    private final List<MineLayer> layers = new ArrayList<>(); // 自上而下

    private int bedrockHeight = 1;
    private Material bedrockMaterial = Material.BEDROCK;
    private int totalHeight = 100;
    private double baseWeightPercent = 90.0;
    private double oreWeightPercent = 10.0;

    public LayerManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    /**
     * 从 layers.yml 加载所有层级定义
     */
    public void loadLayers() {
        layers.clear();
        FileConfiguration cfg = plugin.getLayersConfig();

        // 全局权重
        ConfigurationSection global = cfg.getConfigurationSection("global");
        if (global != null) {
            baseWeightPercent = global.getDouble("base-weight-percent", 90.0);
            oreWeightPercent = global.getDouble("ore-weight-percent", 10.0);
            double sum = baseWeightPercent + oreWeightPercent;
            if (sum > 0) {
                baseWeightPercent = baseWeightPercent / sum * 100.0;
                oreWeightPercent = oreWeightPercent / sum * 100.0;
            }
        }

        // 基岩层
        ConfigurationSection bedrock = cfg.getConfigurationSection("bedrock");
        if (bedrock != null) {
            bedrockHeight = Math.max(1, bedrock.getInt("height", 1));
            try {
                bedrockMaterial = Material.valueOf(bedrock.getString("block", "BEDROCK").toUpperCase());
            } catch (IllegalArgumentException e) {
                bedrockMaterial = Material.BEDROCK;
            }
        }

        // 各层级
        ConfigurationSection layersSection = cfg.getConfigurationSection("layers");
        if (layersSection != null) {
            for (String key : layersSection.getKeys(false)) {
                ConfigurationSection sec = layersSection.getConfigurationSection(key);
                if (sec == null) continue;
                MineLayer layer = parseLayer(key, sec);
                if (layer != null) {
                    layers.add(layer);
                }
            }
        }

        // 兜底：没有任何层级时生成一个纯石头层
        if (layers.isEmpty()) {
            MineLayer fallback = new MineLayer("stone", "&7石头区", 99);
            fallback.addBaseBlock(new MineLayer.WeightedBlock(Material.STONE, 100.0, 1, 1, false));
            layers.add(fallback);
            plugin.getLogger().warning("layers.yml 中没有有效的层级定义，已使用兜底石头层！");
        }

        // 构建继承矿石池（自上而下）
        for (int i = 0; i < layers.size(); i++) {
            layers.get(i).buildOrePool(layers, i, getInheritDecay(global));
        }

        // 计算各层 y 范围（自下而上：y=0 为基岩）
        int y = bedrockHeight;
        for (int i = layers.size() - 1; i >= 0; i--) {
            MineLayer layer = layers.get(i);
            layer.setYRange(y, y + layer.getHeight());
            y += layer.getHeight();
        }
        totalHeight = y; // 已包含基岩高度

        plugin.getLogger().info("分层矿场配置已加载: " + layers.size() + " 个层级，总高度 " + totalHeight + "。");
    }

    private double getInheritDecay(ConfigurationSection global) {
        if (global == null) return 0.08;
        return Math.max(0.0, Math.min(1.0, global.getDouble("inherit-decay", 0.08)));
    }

    private MineLayer parseLayer(String key, ConfigurationSection sec) {
        int height = Math.max(1, sec.getInt("height", 10));
        String display = sec.getString("display", key);
        MineLayer layer = new MineLayer(key, display, height);

        ConfigurationSection base = sec.getConfigurationSection("base-blocks");
        if (base != null) {
            for (String matName : base.getKeys(false)) {
                ConfigurationSection bs = base.getConfigurationSection(matName);
                if (bs == null) continue;
                try {
                    Material material = Material.valueOf(matName.toUpperCase());
                    double weight = bs.getDouble("weight", 10.0);
                    int coin = bs.getInt("coin", 1);
                    int exp = bs.getInt("exp", 1);
                    layer.addBaseBlock(new MineLayer.WeightedBlock(material, weight, coin, exp, false));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("层级 " + key + " 中无效的基石类型: " + matName + "，已跳过。");
                }
            }
        }

        ConfigurationSection ores = sec.getConfigurationSection("ores");
        if (ores != null) {
            for (String matName : ores.getKeys(false)) {
                ConfigurationSection os = ores.getConfigurationSection(matName);
                if (os == null) continue;
                try {
                    Material material = Material.valueOf(matName.toUpperCase());
                    double weight = os.getDouble("weight", 10.0);
                    int coin = os.getInt("coin", 1);
                    int exp = os.getInt("exp", 1);
                    boolean inherit = os.getBoolean("inherit", true);
                    layer.addOre(new MineLayer.WeightedBlock(material, weight, coin, exp, inherit));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("层级 " + key + " 中无效的矿物类型: " + matName + "，已跳过。");
                }
            }
        }
        return layer;
    }

    /**
     * 在指定高度抽取一个方块（含基岩层）
     */
    public Material rollBlockAt(int y, ThreadLocalRandom rnd) {
        if (y < bedrockHeight) {
            return bedrockMaterial;
        }
        MineLayer layer = getLayerAt(y);
        if (layer == null) {
            return Material.STONE;
        }
        return layer.roll(rnd, baseWeightPercent);
    }

    /**
     * 获取指定高度所属的层级（基岩层返回 null）
     */
    public MineLayer getLayerAt(int y) {
        for (MineLayer layer : layers) {
            if (y >= layer.getYMin() && y < layer.getYMax()) {
                return layer;
            }
        }
        return null;
    }

    /**
     * 按 key 查找层级
     */
    public MineLayer getLayer(String key) {
        for (MineLayer layer : layers) {
            if (layer.getKey().equalsIgnoreCase(key)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * 获取层级的 y 范围 [yMin, yMax)。bedrock 返回基岩范围。
     * 找不到返回 null。
     */
    public int[] getYRange(String key) {
        if (key.equalsIgnoreCase("bedrock")) {
            return new int[]{0, bedrockHeight};
        }
        MineLayer layer = getLayer(key);
        if (layer == null) {
            return null;
        }
        return new int[]{layer.getYMin(), layer.getYMax()};
    }

    /**
     * 所有可选层级名（含 bedrock），用于命令补全
     */
    public List<String> getLayerNames() {
        List<String> names = new ArrayList<>();
        for (MineLayer layer : layers) {
            names.add(layer.getKey());
        }
        names.add("bedrock");
        return names;
    }

    /**
     * 层级显示名（未找到时返回 key）
     */
    public String getDisplayName(String key) {
        MineLayer layer = getLayer(key);
        return layer != null ? layer.getDisplayName() : key;
    }

    public List<MineLayer> getLayers() {
        return layers;
    }

    public int getTotalHeight() {
        return totalHeight;
    }

    public int getBedrockHeight() {
        return bedrockHeight;
    }

    public Material getBedrockMaterial() {
        return bedrockMaterial;
    }

    public double getBaseWeightPercent() {
        return baseWeightPercent;
    }

    public double getOreWeightPercent() {
        return oreWeightPercent;
    }
}
