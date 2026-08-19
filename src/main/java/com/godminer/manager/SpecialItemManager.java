package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.ItemBuilder;
import com.godminer.util.LevelUpUtil;
import com.godminer.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 特殊物品管理器 - 经验瓶 / 等级升级球 / 宝箱战利品
 * 物品通过 PersistentDataContainer 携带特殊 NBT，
 * 同类型物品 NBT 一致，可正常堆叠、放入箱子长期保存。
 */
public class SpecialItemManager {

    /** 经验瓶类型 */
    public static class BottleType {
        public String key;
        public String name;
        public int exp;
        public double weight;
        public List<String> lore = new ArrayList<>();
    }

    /** 等级升级球类型 */
    public static class BallType {
        public String key;
        public String name;
        public int levels;
        public double weight;
        public List<String> lore = new ArrayList<>();
    }

    private final GoldMiner plugin;
    private final NamespacedKey keyExpAmount;
    private final NamespacedKey keyLevelAmount;
    private final NamespacedKey keySpecialType;

    private final List<BottleType> bottleTypes = new ArrayList<>();
    private final List<BallType> ballTypes = new ArrayList<>();

    private String expTarget = "mine";
    private String levelTarget = "mine";
    private int bottleStackMin = 1;
    private int bottleStackMax = 16;
    private int ballStackMin = 1;
    private int ballStackMax = 3;
    private int chestMinItems = 2;
    private int chestMaxItems = 10;
    private double chestExpBottleChance = 0.85;
    private double chestLevelBallChance = 0.10;

    public SpecialItemManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.keyExpAmount = new NamespacedKey(plugin, "exp_bottle_exp");
        this.keyLevelAmount = new NamespacedKey(plugin, "level_ball_levels");
        this.keySpecialType = new NamespacedKey(plugin, "special_type");
    }

    /**
     * 从 loot.yml 加载配置
     */
    public void loadConfig() {
        bottleTypes.clear();
        ballTypes.clear();
        FileConfiguration cfg = plugin.getLootConfig();

        ConfigurationSection eb = cfg.getConfigurationSection("exp-bottles");
        if (eb != null) {
            expTarget = eb.getString("exp-target", "mine").toLowerCase();
            bottleStackMin = Math.max(1, eb.getInt("stack-min", 1));
            bottleStackMax = Math.max(bottleStackMin, eb.getInt("stack-max", 16));
            ConfigurationSection types = eb.getConfigurationSection("types");
            if (types != null) {
                for (String k : types.getKeys(false)) {
                    ConfigurationSection t = types.getConfigurationSection(k);
                    if (t == null) continue;
                    BottleType bt = new BottleType();
                    bt.key = k;
                    bt.name = t.getString("name", "&a经验瓶");
                    bt.exp = Math.max(1, t.getInt("exp", 500));
                    bt.weight = Math.max(0.0, t.getDouble("weight", 10.0));
                    bt.lore = t.getStringList("lore");
                    bottleTypes.add(bt);
                }
            }
        }

        ConfigurationSection lb = cfg.getConfigurationSection("level-balls");
        if (lb != null) {
            levelTarget = lb.getString("level-target", "mine").toLowerCase();
            ballStackMin = Math.max(1, lb.getInt("stack-min", 1));
            ballStackMax = Math.max(ballStackMin, lb.getInt("stack-max", 3));
            ConfigurationSection types = lb.getConfigurationSection("types");
            if (types != null) {
                for (String k : types.getKeys(false)) {
                    ConfigurationSection t = types.getConfigurationSection(k);
                    if (t == null) continue;
                    BallType bt = new BallType();
                    bt.key = k;
                    bt.name = t.getString("name", "&e等级升级球");
                    bt.levels = Math.max(1, t.getInt("levels", 1));
                    bt.weight = Math.max(0.0, t.getDouble("weight", 10.0));
                    bt.lore = t.getStringList("lore");
                    ballTypes.add(bt);
                }
            }
        }

        ConfigurationSection chest = cfg.getConfigurationSection("chest-loot");
        if (chest != null) {
            chestMinItems = Math.max(0, chest.getInt("min-items", 2));
            chestMaxItems = Math.max(chestMinItems, chest.getInt("max-items", 10));
            chestExpBottleChance = Math.max(0.0, Math.min(1.0, chest.getDouble("exp-bottle-chance", 0.85)));
            chestLevelBallChance = Math.max(0.0, Math.min(1.0 - chestExpBottleChance, chest.getDouble("level-ball-chance", 0.10)));
        }

        plugin.getLogger().info("特殊物品配置已加载: 经验瓶 " + bottleTypes.size()
                + " 种，等级升级球 " + ballTypes.size() + " 种。");
    }

    // ==================== 物品创建 ====================

    /**
     * 创建指定类型的经验瓶
     */
    public ItemStack createExpBottle(String typeKey, int amount) {
        BottleType type = findBottle(typeKey);
        if (type == null) return null;

        ItemStack item = new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(type.name)
                .lore(type.lore)
                .amount(Math.max(1, Math.min(64, amount)))
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyExpAmount, PersistentDataType.INTEGER, type.exp);
            meta.getPersistentDataContainer().set(keySpecialType, PersistentDataType.STRING, "exp_bottle");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 随机抽取一种经验瓶（数量随机）
     */
    public ItemStack rollExpBottle(ThreadLocalRandom rnd) {
        BottleType type = rollBottleType(rnd);
        if (type == null) return null;
        int amount = rnd.nextInt(bottleStackMin, bottleStackMax + 1);
        return createExpBottle(type.key, amount);
    }

    /**
     * 创建指定类型的等级升级球
     */
    public ItemStack createLevelBall(String typeKey, int amount) {
        BallType type = findBall(typeKey);
        if (type == null) return null;

        ItemStack item = new ItemBuilder(Material.SNOWBALL)
                .name(type.name)
                .lore(type.lore)
                .amount(Math.max(1, Math.min(16, amount)))
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyLevelAmount, PersistentDataType.INTEGER, type.levels);
            meta.getPersistentDataContainer().set(keySpecialType, PersistentDataType.STRING, "level_ball");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 随机抽取一种等级升级球（数量随机）
     */
    public ItemStack rollLevelBall(ThreadLocalRandom rnd) {
        BallType type = rollBallType(rnd);
        if (type == null) return null;
        int amount = rnd.nextInt(ballStackMin, ballStackMax + 1);
        return createLevelBall(type.key, amount);
    }

    // ==================== NBT 读取 ====================

    /**
     * 读取经验瓶给予的经验值；非特殊经验瓶返回 null
     */
    public Integer getExpBottleAmount(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyExpAmount, PersistentDataType.INTEGER);
    }

    /**
     * 读取等级升级球提升的等级数；非特殊雪球返回 null
     */
    public Integer getLevelBallAmount(ItemStack item) {
        if (item == null || item.getType() != Material.SNOWBALL || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyLevelAmount, PersistentDataType.INTEGER);
    }

    // ==================== 使用处理 ====================

    /**
     * 使用经验瓶：消耗一个，给予经验
     */
    public void useExpBottle(Player player, ItemStack item, int exp) {
        if (!consumeOne(item)) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (expTarget.equalsIgnoreCase("vanilla") || data == null) {
            player.giveExp(exp);
        } else {
            data.addExp(exp);
            LevelUpUtil.handleLevelUp(plugin, player, data);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        String msg = plugin.getLangConfig().getString("special-items.exp-bottle-used",
                "&a你使用了 &e经验瓶&a，获得 &e{amount} &a点矿工经验！");
        MessageUtil.sendMessage(player, MessageUtil.replacePlaceholders(msg, "{amount}", String.valueOf(exp)));
    }

    /**
     * 使用等级升级球：消耗一个，提升等级
     */
    public void useLevelBall(Player player, ItemStack item, int levels) {
        if (!consumeOne(item)) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (levelTarget.equalsIgnoreCase("vanilla") || data == null) {
            player.giveExpLevels(levels);
        } else {
            LevelUpUtil.applyLevels(plugin, player, data, levels);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        String msg = plugin.getLangConfig().getString("special-items.level-ball-used",
                "&a你使用了 &e等级升级球&a，矿工等级提升了 &e{levels} &a级！");
        MessageUtil.sendMessage(player, MessageUtil.replacePlaceholders(msg, "{levels}", String.valueOf(levels)));
    }

    /**
     * 从手持物品中消耗一个
     */
    private boolean consumeOne(ItemStack item) {
        if (item.getAmount() <= 0) return false;
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return true;
        }
        item.setAmount(0); // 数量为 0 即被移除
        return true;
    }

    // ==================== 宝箱 ====================

    /**
     * 填充矿洞宝箱
     */
    public void fillChest(Chest chest) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // 直接操作实时方块实体的库存（快照 getInventory()+update() 在部分 Paper 版本
        // 不会把物品写回世界，会导致宝箱为空；getBlockInventory 直接写入世界中的箱子）
        Inventory inv = chest.getBlockInventory();
        inv.clear();

        int itemCount = rnd.nextInt(chestMinItems, chestMaxItems + 1);
        for (int i = 0; i < itemCount; i++) {
            double roll = rnd.nextDouble();
            ItemStack stack;
            if (roll < chestExpBottleChance) {
                stack = rollExpBottle(rnd); // 大概率：经验瓶
            } else if (roll < chestExpBottleChance + chestLevelBallChance) {
                stack = rollLevelBall(rnd); // 极小概率：等级升级球
            } else {
                continue; // 空
            }
            if (stack == null) continue;

            int slot = rnd.nextInt(27);
            int tries = 0;
            while (inv.getItem(slot) != null && tries < 27) {
                slot = (slot + 1) % 27;
                tries++;
            }
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, stack);
            }
        }
    }

    // ==================== 内部方法 ====================

    private BottleType findBottle(String key) {
        for (BottleType t : bottleTypes) {
            if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }

    private BallType findBall(String key) {
        for (BallType t : ballTypes) {
            if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }

    private BottleType rollBottleType(ThreadLocalRandom rnd) {
        return rollWeighted(bottleTypes, rnd, bt -> bt.weight);
    }

    private BallType rollBallType(ThreadLocalRandom rnd) {
        return rollWeighted(ballTypes, rnd, bt -> bt.weight);
    }

    private <T> T rollWeighted(List<T> list, ThreadLocalRandom rnd, java.util.function.ToDoubleFunction<T> weightFn) {
        if (list.isEmpty()) return null;
        double total = 0;
        for (T t : list) total += Math.max(0.0, weightFn.applyAsDouble(t));
        if (total <= 0) return list.get(0);
        double roll = rnd.nextDouble() * total;
        double cumulative = 0;
        for (T t : list) {
            cumulative += Math.max(0.0, weightFn.applyAsDouble(t));
            if (roll <= cumulative) return t;
        }
        return list.get(list.size() - 1);
    }
}
