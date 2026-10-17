package top.nekolove.goldminer.manager;

import top.nekolove.goldminer.GoldMiner;
import top.nekolove.goldminer.model.PlayerData;
import top.nekolove.goldminer.util.ItemBuilder;
import top.nekolove.goldminer.util.LevelUpUtil;
import top.nekolove.goldminer.util.MessageUtil;
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
 * 特殊物品管理器 - 经验瓶 / 等级升级球 / 金币袋 / 宝箱战利品
 * 物品通过 PersistentDataContainer 携带特殊 NBT，
 * 同类型物品 NBT 一致，可正常堆叠、放入箱子长期保存。
 * 金币袋为一次性不可堆叠物品，不同颜色的收纳袋携带不同数值的 NBT。
 */
public class SpecialItemManager {

    /** 经验瓶类型 */
    public static class BottleType {
        public String key;
        public String name;
        public int exp;
        public double weight;
        public boolean premium; // 高数值类型（硫黄矿洞以更高权重抽取）
        public List<String> lore = new ArrayList<>();
    }

    /** 等级升级球类型 */
    public static class BallType {
        public String key;
        public String name;
        public int levels;
        public double weight;
        public boolean premium;
        public List<String> lore = new ArrayList<>();
    }

    /** 金币袋类型（不同颜色的收纳袋） */
    public static class CoinBagType {
        public String key;
        public Material material;
        public String name;
        public int coins;
        public double weight;
        public boolean premium;
        public List<String> lore = new ArrayList<>();
    }

    private final GoldMiner plugin;
    private final NamespacedKey keyExpAmount;
    private final NamespacedKey keyLevelAmount;
    private final NamespacedKey keyCoinBagCoins;
    private final NamespacedKey keySpecialType;

    private final List<BottleType> bottleTypes = new ArrayList<>();
    private final List<BallType> ballTypes = new ArrayList<>();
    private final List<CoinBagType> coinBagTypes = new ArrayList<>();

    private String expTarget = "mine";
    private String levelTarget = "mine";
    private Material coinBagDefaultMaterial = Material.BUNDLE;
    private int bottleStackMin = 1;
    private int bottleStackMax = 16;
    private int ballStackMin = 1;
    private int ballStackMax = 3;
    private int chestMinItems = 2;
    private int chestMaxItems = 10;
    private double chestExpBottleChance = 0.85;
    private double chestLevelBallChance = 0.10;
    private double chestCoinBagChance = 0.04;

    // 硫黄矿洞宝箱加成
    private int sulfurMinItems = 6;
    private int sulfurMaxItems = 14;
    private double sulfurExpChance = 0.70;
    private double sulfurBallChance = 0.20;
    private double sulfurBagChance = 0.30;
    private double premiumWeightMultiplier = 3.0;
    private double sulfurStackMultiplier = 1.5;

    public SpecialItemManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.keyExpAmount = new NamespacedKey(plugin, "exp_bottle_exp");
        this.keyLevelAmount = new NamespacedKey(plugin, "level_ball_levels");
        this.keyCoinBagCoins = new NamespacedKey(plugin, "coin_bag_coins");
        this.keySpecialType = new NamespacedKey(plugin, "special_type");
    }

    /**
     * 从 loot.yml 加载配置
     */
    public void loadConfig() {
        bottleTypes.clear();
        ballTypes.clear();
        coinBagTypes.clear();
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
                    bt.premium = t.getBoolean("premium", false);
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
                    bt.premium = t.getBoolean("premium", false);
                    bt.lore = t.getStringList("lore");
                    ballTypes.add(bt);
                }
            }
        }

        // 金币袋（不同颜色的收纳袋）
        ConfigurationSection cb = cfg.getConfigurationSection("coin-bags");
        if (cb != null) {
            try {
                coinBagDefaultMaterial = Material.valueOf(
                        cb.getString("material", "BUNDLE").toUpperCase());
            } catch (IllegalArgumentException e) {
                coinBagDefaultMaterial = Material.BUNDLE;
            }
            ConfigurationSection types = cb.getConfigurationSection("types");
            if (types != null) {
                for (String k : types.getKeys(false)) {
                    ConfigurationSection t = types.getConfigurationSection(k);
                    if (t == null) continue;
                    CoinBagType ct = new CoinBagType();
                    ct.key = k;
                    String matName = t.getString("material", coinBagDefaultMaterial.name());
                    try {
                        ct.material = Material.valueOf(matName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        ct.material = coinBagDefaultMaterial;
                        plugin.getLogger().warning("金币袋类型 " + k + " 的材质无效: " + matName
                                + "，已回退为 " + coinBagDefaultMaterial.name() + "。");
                    }
                    ct.name = t.getString("name", "&e金币袋");
                    ct.coins = Math.max(1, t.getInt("coins", 1000));
                    ct.weight = Math.max(0.0, t.getDouble("weight", 10.0));
                    ct.premium = t.getBoolean("premium", false);
                    ct.lore = t.getStringList("lore");
                    coinBagTypes.add(ct);
                }
            }
        }

        ConfigurationSection chest = cfg.getConfigurationSection("chest-loot");
        if (chest != null) {
            chestMinItems = Math.max(0, chest.getInt("min-items", 2));
            chestMaxItems = Math.max(chestMinItems, chest.getInt("max-items", 10));
            chestExpBottleChance = Math.max(0.0, Math.min(1.0, chest.getDouble("exp-bottle-chance", 0.85)));
            chestLevelBallChance = Math.max(0.0, Math.min(1.0 - chestExpBottleChance, chest.getDouble("level-ball-chance", 0.10)));
            chestCoinBagChance = Math.max(0.0, Math.min(1.0 - chestExpBottleChance - chestLevelBallChance,
                    chest.getDouble("coin-bag-chance", 0.04)));

            // 硫黄矿洞宝箱加成
            ConfigurationSection sulfur = chest.getConfigurationSection("sulfur-cave");
            if (sulfur != null) {
                sulfurMinItems = Math.max(0, sulfur.getInt("min-items", 6));
                sulfurMaxItems = Math.max(sulfurMinItems, sulfur.getInt("max-items", 14));
                sulfurExpChance = Math.max(0.0, Math.min(1.0, sulfur.getDouble("exp-bottle-chance", 0.70)));
                sulfurBallChance = Math.max(0.0, Math.min(1.0 - sulfurExpChance, sulfur.getDouble("level-ball-chance", 0.20)));
                sulfurBagChance = Math.max(0.0, Math.min(1.0 - sulfurExpChance - sulfurBallChance,
                        sulfur.getDouble("coin-bag-chance", 0.30)));
                premiumWeightMultiplier = Math.max(1.0, sulfur.getDouble("premium-weight-multiplier", 3.0));
                sulfurStackMultiplier = Math.max(1.0, sulfur.getDouble("stack-multiplier", 1.5));
            }
        }

        plugin.getLogger().info("特殊物品配置已加载: 经验瓶 " + bottleTypes.size()
                + " 种，等级升级球 " + ballTypes.size() + " 种，金币袋 " + coinBagTypes.size() + " 种。");
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
     * @param stackMult 数量放大倍率（硫黄矿洞加成）
     * @param sulfur    是否硫黄矿洞（高数值类型权重更高）
     */
    public ItemStack rollExpBottle(ThreadLocalRandom rnd, double stackMult, boolean sulfur) {
        BottleType type = rollBottleType(rnd, sulfur);
        if (type == null) return null;
        int amount = (int) Math.round(rnd.nextInt(bottleStackMin, bottleStackMax + 1) * stackMult);
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
     * @param stackMult 数量放大倍率（硫黄矿洞加成）
     * @param sulfur    是否硫黄矿洞（高数值类型权重更高）
     */
    public ItemStack rollLevelBall(ThreadLocalRandom rnd, double stackMult, boolean sulfur) {
        BallType type = rollBallType(rnd, sulfur);
        if (type == null) return null;
        int amount = (int) Math.round(rnd.nextInt(ballStackMin, ballStackMax + 1) * stackMult);
        return createLevelBall(type.key, amount);
    }

    // ==================== 金币袋 ====================

    /**
     * 创建指定类型的金币袋（一次性、不可堆叠）
     */
    public ItemStack createCoinBag(String typeKey) {
        CoinBagType type = findCoinBag(typeKey);
        if (type == null) return null;

        List<String> lore = new ArrayList<>();
        for (String line : type.lore) {
            lore.add(MessageUtil.replacePlaceholders(line,
                    "{coins}", MessageUtil.formatNumber(type.coins)));
        }

        ItemStack item = new ItemBuilder(type.material)
                .name(type.name)
                .lore(lore)
                .amount(1)
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyCoinBagCoins, PersistentDataType.INTEGER, type.coins);
            meta.getPersistentDataContainer().set(keySpecialType, PersistentDataType.STRING, "coin_bag");
            // 一次性物品，不可堆叠
            if (meta.hasMaxStackSize()) {
                meta.setMaxStackSize(1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 随机抽取一种金币袋（硫黄矿洞中高数值金币袋权重更高）
     */
    public ItemStack rollCoinBag(ThreadLocalRandom rnd, boolean sulfur) {
        CoinBagType type = rollCoinBagType(rnd, sulfur);
        if (type == null) return null;
        return createCoinBag(type.key);
    }

    /**
     * 判断材质是否为收纳袋（含各颜色收纳袋）
     */
    public boolean isBundle(Material material) {
        if (material == null) return false;
        return material == Material.BUNDLE || material.name().endsWith("_BUNDLE");
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

    /**
     * 读取金币袋的金币数；非特殊收纳袋返回 null
     */
    public Integer getCoinBagCoins(ItemStack item) {
        if (item == null || !isBundle(item.getType()) || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyCoinBagCoins, PersistentDataType.INTEGER);
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
     * 使用金币袋：回收物品，根据 NBT 给予金币并提示
     */
    public void useCoinBag(Player player, ItemStack item, int coins) {
        if (!consumeOne(item)) return;

        plugin.getEconomyManager().addCoins(player, coins);

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        String msg = plugin.getLangConfig().getString("special-items.coin-bag-used",
                "&a你打开了 &e{name}&a，获得 &6{coins} &a金币！");
        msg = MessageUtil.replacePlaceholders(msg,
                "{name}", getItemDisplayName(item),
                "{coins}", MessageUtil.formatNumber(coins));
        MessageUtil.sendMessage(player, msg);
    }

    /**
     * 获取物品显示名（无自定义名时回退为"金币袋"）
     */
    private String getItemDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            net.kyori.adventure.text.Component displayName = item.getItemMeta().displayName();
            if (displayName != null) {
                return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().serialize(displayName);
            }
        }
        return "金币袋";
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
     * 填充矿洞宝箱（普通矿洞）
     */
    public void fillChest(Chest chest) {
        fillChest(chest, false);
    }

    /**
     * 填充矿洞宝箱
     * @param sulfurCave 是否硫黄矿洞（宝箱爆率与高数值物品概率提升）
     */
    public void fillChest(Chest chest, boolean sulfurCave) {
        // 使用 getInventory()：大箱子（双箱）会返回 54 格完整库存并直接写入世界，
        // getBlockInventory() 只返回该半边 27 格，双箱时只填充一半。
        Inventory inv = chest.getInventory();
        if (inv == null) return;
        inv.clear();
        fillInventory(inv, sulfurCave);
    }

    /**
     * 填充箱子矿车（大型矿洞的宝箱替代品）
     */
    public void fillStorageMinecart(org.bukkit.entity.minecart.StorageMinecart cart, boolean sulfurCave) {
        Inventory inv = cart.getInventory();
        inv.clear();
        fillInventory(inv, sulfurCave);
    }

    /**
     * 按矿洞类型填充一个库存（经验瓶/等级升级球/金币袋）
     */
    private void fillInventory(Inventory inv, boolean sulfurCave) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        int minItems = sulfurCave ? sulfurMinItems : chestMinItems;
        int maxItems = sulfurCave ? sulfurMaxItems : chestMaxItems;
        double expChance = sulfurCave ? sulfurExpChance : chestExpBottleChance;
        double ballChance = sulfurCave ? sulfurBallChance : chestLevelBallChance;
        double bagChance = sulfurCave ? sulfurBagChance : chestCoinBagChance;
        double stackMult = sulfurCave ? sulfurStackMultiplier : 1.0;

        int itemCount = rnd.nextInt(minItems, maxItems + 1);
        int size = inv.getSize();
        for (int i = 0; i < itemCount; i++) {
            double roll = rnd.nextDouble();
            ItemStack stack;
            if (roll < expChance) {
                stack = rollExpBottle(rnd, stackMult, sulfurCave); // 大概率：经验瓶
            } else if (roll < expChance + ballChance) {
                stack = rollLevelBall(rnd, stackMult, sulfurCave); // 等级升级球
            } else if (roll < expChance + ballChance + bagChance) {
                stack = rollCoinBag(rnd, sulfurCave); // 金币袋
            } else {
                continue; // 空
            }
            if (stack == null) continue;

            int slot = rnd.nextInt(size);
            int tries = 0;
            while (inv.getItem(slot) != null && tries < size) {
                slot = (slot + 1) % size;
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

    private CoinBagType findCoinBag(String key) {
        for (CoinBagType t : coinBagTypes) {
            if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }

    private BottleType rollBottleType(ThreadLocalRandom rnd, boolean sulfur) {
        return rollWeighted(bottleTypes, rnd, bt -> bt.weight * premiumFactor(bt.premium, sulfur));
    }

    private BallType rollBallType(ThreadLocalRandom rnd, boolean sulfur) {
        return rollWeighted(ballTypes, rnd, bt -> bt.weight * premiumFactor(bt.premium, sulfur));
    }

    private CoinBagType rollCoinBagType(ThreadLocalRandom rnd, boolean sulfur) {
        return rollWeighted(coinBagTypes, rnd, bt -> bt.weight * premiumFactor(bt.premium, sulfur));
    }

    /**
     * 硫黄矿洞中高数值（premium）类型的权重放大倍率
     */
    private double premiumFactor(boolean premium, boolean sulfur) {
        if (premium && sulfur) return premiumWeightMultiplier;
        return 1.0;
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
