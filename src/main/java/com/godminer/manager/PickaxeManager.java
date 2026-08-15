package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PickaxeTier;
import com.godminer.model.PlayerData;
import com.godminer.util.ItemBuilder;
import com.godminer.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 镐子管理器 - 管理镐子的升级和附魔
 */
public class PickaxeManager {

    private final GoldMiner plugin;

    // 每个镐子等级的默认附魔
    private static final List<PickaxeTier> TIER_PROGRESSION = Arrays.asList(
            PickaxeTier.WOOD, PickaxeTier.STONE, PickaxeTier.COPPER, PickaxeTier.GOLD,
            PickaxeTier.IRON, PickaxeTier.DIAMOND, PickaxeTier.NETHERITE
    );

    public PickaxeManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取初始木镐（原初之镐）
     */
    public ItemStack createInitialPickaxe() {
        return new ItemBuilder(Material.WOODEN_PICKAXE)
                .name(plugin.getLangConfig().getString("pickaxe.wood", "&7原初之镐"))
                .enchant(Enchantment.EFFICIENCY, 1)
                .enchant(Enchantment.UNBREAKING, 1)
                .unbreakable(true)
                .flags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS)
                .build();
    }

    /**
     * 创建指定等级的镐子
     */
    public ItemStack createPickaxe(PlayerData data) {
        PickaxeTier tier = data.getPickaxeTier();
        Material material = tier.getBaseMaterial();

        String configKey = "pickaxe." + tier.getConfigKey();
        String name = plugin.getLangConfig().getString(configKey, tier.name() + "镐");

        ItemBuilder builder = new ItemBuilder(material)
                .name(name)
                .unbreakable(true)
                .flags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);

        // 添加附魔
        if (data.getEfficiencyLevel() > 0) {
            builder.enchant(Enchantment.EFFICIENCY, data.getEfficiencyLevel());
        }
        if (data.getFortuneLevel() > 0) {
            builder.enchant(Enchantment.FORTUNE, data.getFortuneLevel());
        }
        if (data.getUnbreakingLevel() > 0) {
            builder.enchant(Enchantment.UNBREAKING, data.getUnbreakingLevel());
        }

        return builder.build();
    }

    /**
     * 检查并处理镐子升级
     * 返回升级信息，如果没升级则返回null
     */
    public String checkAndUpgradePickaxe(PlayerData data) {
        int level = data.getLevel();
        PickaxeTier currentTier = data.getPickaxeTier();

        int maxEff = getMaxEfficiency(data);
        int maxFortune = getMaxFortune(data);
        int maxUnbreaking = getMaxUnbreaking(data);
        int currentEff = data.getEfficiencyLevel();
        int currentFortune = data.getFortuneLevel();
        int currentUnbreaking = data.getUnbreakingLevel();

        boolean efficiencyCapped = currentEff >= maxEff;
        boolean fortuneCapped = currentFortune >= maxFortune;
        boolean unbreakingCapped = currentUnbreaking >= maxUnbreaking;

        if (efficiencyCapped && fortuneCapped && unbreakingCapped) {
            PickaxeTier nextTier = getNextTier(currentTier);
            if (nextTier != currentTier) {
                int levelsNeeded = getLevelsForNextTier(currentTier);
                if (data.getLevelsInCurrentTier() < levelsNeeded) {
                    return null; // 等级不够，不晋升
                }
                // 晋升到下一个镐子等级
                data.setPickaxeTier(nextTier);
                data.setLevelsInCurrentTier(0);
                data.setEfficiencyLevel(0);
                data.setFortuneLevel(0);
                data.setUnbreakingLevel(0);
                inheritRandomMaxEnchant(data, currentTier);
                return "pickaxe-upgrade";
            }
            return null; // 已是最高等级
        }

        // 逐级升级附魔
        if (currentEff < maxEff) {
            data.setEfficiencyLevel(currentEff + 1);
            return "enchant-upgrade";
        } else if (currentFortune < maxFortune) {
            data.setFortuneLevel(currentFortune + 1);
            return "enchant-upgrade";
        } else if (currentUnbreaking < maxUnbreaking) {
            data.setUnbreakingLevel(currentUnbreaking + 1);
            return "enchant-upgrade";
        }

        return null;
    }

    private PickaxeTier getNextTier(PickaxeTier current) {
        int idx = TIER_PROGRESSION.indexOf(current);
        if (idx >= 0 && idx < TIER_PROGRESSION.size() - 1) {
            return TIER_PROGRESSION.get(idx + 1);
        }
        return current;
    }

    private int getLevelsForNextTier(PickaxeTier current) {
        // 木->石: 2级
        // 石->铜, 铜->金: 20级
        // 金->铁, 铁->钻石, 钻石->下界合金: 30级
        if (current == PickaxeTier.WOOD) return 2;
        if (current == PickaxeTier.STONE || current == PickaxeTier.COPPER) return 20;
        return 30;
    }

    private void inheritRandomMaxEnchant(PlayerData data, PickaxeTier previousTier) {
        // 随机选择一个满级附魔继承
        int maxEff = getMaxEfficiency(previousTier);
        int maxFortune = getMaxFortune(previousTier);
        int maxUnbreaking = getMaxUnbreaking(previousTier);

        List<Runnable> enchants = new ArrayList<>();
        if (maxEff > 0) enchants.add(() -> data.setEfficiencyLevel(maxEff));
        if (maxFortune > 0) enchants.add(() -> data.setFortuneLevel(maxFortune));
        if (maxUnbreaking > 0) enchants.add(() -> data.setUnbreakingLevel(maxUnbreaking));

        if (!enchants.isEmpty()) {
            int idx = ThreadLocalRandom.current().nextInt(enchants.size());
            enchants.get(idx).run();
        }
    }

    /**
     * 获取当前最大效率等级
     */
    public int getMaxEfficiency(PlayerData data) {
        return getMaxEfficiency(data.getPickaxeTier());
    }

    public int getMaxEfficiency(PickaxeTier tier) {
        if (tier.hasNetheriteEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.netherite.efficiency", 255);
        } else if (tier.hasDiamondEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.diamond.efficiency", 30);
        } else if (tier.hasExtendedEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.iron.efficiency", 10);
        }
        return plugin.getConfig().getInt("pickaxe-enchant-limits.default.efficiency", 5);
    }

    /**
     * 获取当前最大时运等级
     */
    public int getMaxFortune(PlayerData data) {
        return getMaxFortune(data.getPickaxeTier());
    }

    public int getMaxFortune(PickaxeTier tier) {
        if (tier.hasNetheriteEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.netherite.fortune", 15);
        } else if (tier.hasDiamondEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.diamond.fortune", 10);
        }
        return plugin.getConfig().getInt("pickaxe-enchant-limits.default.fortune", 3);
    }

    /**
     * 获取当前最大耐久等级
     */
    public int getMaxUnbreaking(PlayerData data) {
        return getMaxUnbreaking(data.getPickaxeTier());
    }

    public int getMaxUnbreaking(PickaxeTier tier) {
        if (tier.hasNetheriteEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.netherite.unbreaking", 10);
        } else if (tier.hasDiamondEnchantLimits()) {
            return plugin.getConfig().getInt("pickaxe-enchant-limits.diamond.unbreaking", 5);
        }
        return plugin.getConfig().getInt("pickaxe-enchant-limits.default.unbreaking", 3);
    }

    /**
     * 检查附魔是否可升级
     */
    public boolean canUpgradeEnchant(PickaxeTier tier, int currentEff, int currentFortune) {
        return currentEff < getMaxEfficiency(tier) || currentFortune < getMaxFortune(tier);
    }

    // ===== 盔甲系统 =====

    private static final List<Enchantment> HELMET_ENCHANTS = Arrays.asList(
            Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
            Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
            Enchantment.AQUA_AFFINITY, Enchantment.RESPIRATION,
            Enchantment.THORNS, Enchantment.UNBREAKING, Enchantment.MENDING
    );

    private static final List<Enchantment> CHESTPLATE_ENCHANTS = Arrays.asList(
            Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
            Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
            Enchantment.THORNS, Enchantment.UNBREAKING, Enchantment.MENDING
    );

    private static final List<Enchantment> LEGGINGS_ENCHANTS = Arrays.asList(
            Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
            Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
            Enchantment.SWIFT_SNEAK, Enchantment.THORNS,
            Enchantment.UNBREAKING, Enchantment.MENDING
    );

    private static final List<Enchantment> BOOTS_ENCHANTS = Arrays.asList(
            Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
            Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
            Enchantment.DEPTH_STRIDER, Enchantment.FEATHER_FALLING,
            Enchantment.FROST_WALKER, Enchantment.SOUL_SPEED,
            Enchantment.SWIFT_SNEAK, Enchantment.THORNS,
            Enchantment.UNBREAKING, Enchantment.MENDING
    );

    /**
     * 镐子等级 → 盔甲材质映射
     * WOOD=皮革, STONE=锁链, COPPER=铜, GOLD=金, IRON=铁, DIAMOND=钻石, NETHERITE=下界合金
     */
    private Material getArmorMaterial(PickaxeTier tier, String piece) {
        switch (tier) {
            case WOOD:
                switch (piece) {
                    case "helmet": return Material.LEATHER_HELMET;
                    case "chestplate": return Material.LEATHER_CHESTPLATE;
                    case "leggings": return Material.LEATHER_LEGGINGS;
                    case "boots": return Material.LEATHER_BOOTS;
                }
            case STONE:
                switch (piece) {
                    case "helmet": return Material.CHAINMAIL_HELMET;
                    case "chestplate": return Material.CHAINMAIL_CHESTPLATE;
                    case "leggings": return Material.CHAINMAIL_LEGGINGS;
                    case "boots": return Material.CHAINMAIL_BOOTS;
                }
            case COPPER:
                switch (piece) {
                    case "helmet": return Material.COPPER_HELMET;
                    case "chestplate": return Material.COPPER_CHESTPLATE;
                    case "leggings": return Material.COPPER_LEGGINGS;
                    case "boots": return Material.COPPER_BOOTS;
                }
            case GOLD:
                switch (piece) {
                    case "helmet": return Material.GOLDEN_HELMET;
                    case "chestplate": return Material.GOLDEN_CHESTPLATE;
                    case "leggings": return Material.GOLDEN_LEGGINGS;
                    case "boots": return Material.GOLDEN_BOOTS;
                }
            case IRON:
                switch (piece) {
                    case "helmet": return Material.IRON_HELMET;
                    case "chestplate": return Material.IRON_CHESTPLATE;
                    case "leggings": return Material.IRON_LEGGINGS;
                    case "boots": return Material.IRON_BOOTS;
                }
            case DIAMOND:
                switch (piece) {
                    case "helmet": return Material.DIAMOND_HELMET;
                    case "chestplate": return Material.DIAMOND_CHESTPLATE;
                    case "leggings": return Material.DIAMOND_LEGGINGS;
                    case "boots": return Material.DIAMOND_BOOTS;
                }
            case NETHERITE:
                switch (piece) {
                    case "helmet": return Material.NETHERITE_HELMET;
                    case "chestplate": return Material.NETHERITE_CHESTPLATE;
                    case "leggings": return Material.NETHERITE_LEGGINGS;
                    case "boots": return Material.NETHERITE_BOOTS;
                }
            default: return Material.AIR;
        }
    }

    private String getArmorConfigKey(PickaxeTier tier) {
        switch (tier) {
            case WOOD: return "armor.leather";
            case STONE: return "armor.chainmail";
            case COPPER: return "armor.copper";
            case GOLD: return "armor.golden";
            case IRON: return "armor.iron";
            case DIAMOND: return "armor.diamond";
            case NETHERITE: return "armor.netherite";
            default: return "";
        }
    }

    private ItemStack createArmorPiece(PickaxeTier tier, String piece) {
        Material armorMat = getArmorMaterial(tier, piece);
        if (armorMat == Material.AIR) return null;

        String configKey = getArmorConfigKey(tier);
        // 直接使用套装名称，不加头盔/胸甲后缀
        String setName = plugin.getLangConfig().getString(configKey + ".name", "矿工装备");
        String loreText = plugin.getLangConfig().getString(configKey + ".lore", "");

        ItemBuilder builder = new ItemBuilder(armorMat)
                .name("&d" + setName)
                .unbreakable(true)
                .flags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS,
                       ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ARMOR_TRIM);

        if (!loreText.isEmpty()) {
            builder.addLore(loreText);
        }

        // 添加全套最大等级附魔
        List<Enchantment> enchants;
        switch (piece) {
            case "helmet": enchants = HELMET_ENCHANTS; break;
            case "chestplate": enchants = CHESTPLATE_ENCHANTS; break;
            case "leggings": enchants = LEGGINGS_ENCHANTS; break;
            case "boots": enchants = BOOTS_ENCHANTS; break;
            default: enchants = CHESTPLATE_ENCHANTS;
        }

        for (Enchantment ench : enchants) {
            int maxLvl = ench.getMaxLevel();
            if (maxLvl > 0) {
                builder.enchant(ench, maxLvl);
            }
        }

        // 构建ItemStack后再通过ItemMeta添加锻造纹饰
        ItemStack item = builder.build();
        addArmorTrim(item, tier);
        return item;
    }

    /**
     * 给装备添加锻造模板纹路（使用反射兼容不同API版本）
     */
    private void addArmorTrim(ItemStack item, PickaxeTier tier) {
        try {
            var meta = item.getItemMeta();
            if (meta == null) return;

            // 反射获取 TrimPattern 和 TrimMaterial 类
            Class<?> trimPatternClass = Class.forName("org.bukkit.inventory.meta.trim.TrimPattern");
            Class<?> trimMaterialClass = Class.forName("org.bukkit.inventory.meta.trim.TrimMaterial");
            Class<?> armorTrimClass = Class.forName("org.bukkit.inventory.meta.trim.ArmorTrim");

            // 获取 TrimPatterns 枚举值
            String patternName;
            String materialName;
            switch (tier) {
                case WOOD:  patternName = "SENTRY";  materialName = "IRON";     break;
                case STONE: patternName = "DUNE";    materialName = "IRON";     break;
                case COPPER: patternName = "COAST";  materialName = "COPPER";   break;
                case GOLD:   patternName = "WILD";   materialName = "GOLD";     break;
                case IRON:   patternName = "TIDE";   materialName = "IRON";     break;
                case DIAMOND: patternName = "EYE";   materialName = "DIAMOND";  break;
                case NETHERITE: patternName = "SILENCE"; materialName = "NETHERITE"; break;
                default: return;
            }

            Object pattern = trimPatternClass.getField(patternName).get(null);
            Object material = trimMaterialClass.getField(materialName).get(null);
            Object trim = armorTrimClass.getConstructor(trimMaterialClass, trimPatternClass).newInstance(material, pattern);

            // 调用 meta.setTrim (ArmorMeta 接口)
            var setTrimMethod = meta.getClass().getMethod("setTrim", armorTrimClass);
            setTrimMethod.invoke(meta, trim);
            item.setItemMeta(meta);
        } catch (Exception e) {
            // 如果版本不支持，静默忽略纹饰
        }
    }

    /**
     * 创建全套盔甲
     */
    public ItemStack[] createFullArmor(PickaxeTier tier) {
        return new ItemStack[]{
                createArmorPiece(tier, "boots"),
                createArmorPiece(tier, "leggings"),
                createArmorPiece(tier, "chestplate"),
                createArmorPiece(tier, "helmet")
        };
    }

    /**
     * 给玩家穿戴对应等级装备
     */
    public void equipArmor(Player player, PlayerData data) {
        ItemStack[] armor = createFullArmor(data.getPickaxeTier());
        var inv = player.getInventory();
        inv.setBoots(armor[0]);
        inv.setLeggings(armor[1]);
        inv.setChestplate(armor[2]);
        inv.setHelmet(armor[3]);
    }

    /**
     * 清除玩家装备
     */
    public void clearArmor(Player player) {
        var inv = player.getInventory();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
    }

    /**
     * 检查玩家是否穿戴了正确的矿场装备
     */
    public boolean hasCorrectArmor(Player player, PlayerData data) {
        if (!data.isSuitVisible()) return true; // 已卸下，不检查
        var inv = player.getInventory();
        PickaxeTier tier = data.getPickaxeTier();

        Material helmMat = getArmorMaterial(tier, "helmet");
        Material chestMat = getArmorMaterial(tier, "chestplate");
        Material legMat = getArmorMaterial(tier, "leggings");
        Material bootMat = getArmorMaterial(tier, "boots");

        var helm = inv.getHelmet();
        var chest = inv.getChestplate();
        var legs = inv.getLeggings();
        var boots = inv.getBoots();

        if (helm == null || helm.getType() != helmMat) return false;
        if (chest == null || chest.getType() != chestMat) return false;
        if (legs == null || legs.getType() != legMat) return false;
        if (boots == null || boots.getType() != bootMat) return false;

        return true;
    }

    /**
     * 创建无限玻璃
     */
    public ItemStack createGlass() {
        String matName = plugin.getConfig().getString("glass-block.material", "GLASS");
        Material glassMat;
        try {
            glassMat = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            glassMat = Material.GLASS;
        }
        return new ItemBuilder(glassMat)
                .name(plugin.getConfig().getString("glass-block.name", "&f垫脚玻璃 &7(无限使用)"))
                .lore(plugin.getConfig().getString("glass-block.lore", "&7可无限放置的玻璃方块"))
                .amount(64)
                .build();
    }
}
