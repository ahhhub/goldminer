package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 商店管理器 - 管理矿场商店的价格和购买逻辑
 */
public class ShopManager {

    private final GoldMiner plugin;
    private FileConfiguration shopConfig;
    private File shopFile;

    public ShopManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    public void init() {
        shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        // 合并默认值
        InputStream defaultStream = plugin.getResource("shop.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            shopConfig.setDefaults(defaultConfig);
        }
        plugin.getLogger().info("商店配置已加载。");
    }

    public void reloadShopConfig() {
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        InputStream defaultStream = plugin.getResource("shop.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            shopConfig.setDefaults(defaultConfig);
        }
    }

    public FileConfiguration getShopConfig() {
        return shopConfig;
    }

    public void saveShopConfig() {
        try {
            shopConfig.save(shopFile);
        } catch (Exception e) {
            plugin.getLogger().severe("保存 shop.yml 失败: " + e.getMessage());
        }
    }

    // ==================== 价格计算 ====================

    /**
     * 安全转换为价格（防止int溢出，上限21亿）
     */
    private int safePrice(double value) {
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value <= 0) return 1;
        return (int) Math.round(value);
    }

    /**
     * 计算药水价格
     * 公式: basePrice * (1 + level * levelMultiplier) * (1 + durationIndex * durationMultiplier)
     */
    public int calculatePotionPrice(String potionType, int level, int durationIndex) {
        int basePrice = shopConfig.getInt("potion." + potionType + ".base-price", 30);
        double levelMultiplier = shopConfig.getDouble("potion." + potionType + ".level-multiplier", 0.5);
        double durationMultiplier = shopConfig.getDouble("potion." + potionType + ".duration-multiplier", 0.3);
        return safePrice(basePrice * (1 + level * levelMultiplier) * (1 + durationIndex * durationMultiplier));
    }

    /**
     * 计算暴击率价格 - 阶梯式涨价
     * 公式: basePrice * tierMultiplier ^ tierIndex
     */
    public int calculateCritRatePrice(double rateIncrease) {
        double basePrice = shopConfig.getDouble("crit-rate.base-price", 300);
        String key = formatRateKey(rateIncrease);
        double multiplier = shopConfig.getDouble("crit-rate.tiers." + key, getDefaultCritRateMultiplier(rateIncrease));
        return safePrice(basePrice * multiplier);
    }

    private double getDefaultCritRateMultiplier(double rate) {
        if (rate <= 0.005) return 1.0;
        if (rate <= 0.01) return 3.0;
        if (rate <= 0.05) return 15.0;
        if (rate <= 0.10) return 50.0;
        return 300.0; // 50%
    }

    private String formatRateKey(double rate) {
        if (rate <= 0.005) return "0.5pct";
        if (rate <= 0.01) return "1pct";
        if (rate <= 0.05) return "5pct";
        if (rate <= 0.10) return "10pct";
        return "50pct";
    }

    /**
     * 计算暴击倍率价格（阶梯式涨价）
     */
    public int calculateCritMagPrice(double magnificationIncrease) {
        double basePrice = shopConfig.getDouble("crit-mag.base-price", 150);
        String key = "x" + ((int) magnificationIncrease);
        double multiplier = shopConfig.getDouble("crit-mag.tiers." + key, getDefaultCritMagMultiplier(magnificationIncrease));
        return safePrice(basePrice * multiplier);
    }

    private double getDefaultCritMagMultiplier(double mag) {
        if (mag <= 1) return 1.0;      // 1倍: 150
        if (mag <= 5) return 8.0;      // 5倍: 1200
        if (mag <= 8) return 20.0;     // 8倍: 3000
        if (mag <= 10) return 35.0;    // 10倍: 5250
        if (mag <= 15) return 70.0;    // 15倍: 10500
        return 150.0;                   // 20倍: 22500
    }

    /**
     * 计算购买等级的价格
     * 公式: 从当前等级到目标等级所需总经验 × 经验单价系数
     */
    public int calculateLevelPrice(PlayerData data, int levelsToBuy) {
        int targetLevel = data.getLevel() + levelsToBuy;
        int totalExpNeeded = data.getExpToTargetLevel(targetLevel);
        double expPriceRate = shopConfig.getDouble("level-buy.exp-price-rate", 0.5);
        return safePrice(totalExpNeeded * expPriceRate);
    }

    /**
     * 计算购买指定数量等级的价格
     */
    public int calculateLevelPriceForAmount(PlayerData data, int amount) {
        return calculateLevelPrice(data, amount);
    }

    // ==================== 购买操作 ====================

    /**
     * 通用金币不足检查
     */
    public boolean checkAndDeductCoins(Player player, PlayerData data, int price, String itemName) {
        if (!data.removeCoins(price)) {
            int diff = price - data.getCoins();
            String msg = shopConfig.getString("messages.insufficient-coins",
                    "&c金币不足！当前金币余额：&6{balance} &c购买当前商品所需金币：&6{price} &c当前还差：&6{diff}");
            msg = msg.replace("{balance}", MessageUtil.formatNumber(data.getCoins()))
                     .replace("{price}", MessageUtil.formatNumber(price))
                     .replace("{diff}", MessageUtil.formatNumber(diff))
                     .replace("{item}", itemName);
            MessageUtil.sendMessage(player, msg);
            return false;
        }
        return true;
    }

    /**
     * 购买药水效果
     */
    public boolean buyPotionEffect(Player player, PlayerData data, String potionType, int level, int durationSeconds) {
        int[] durations = shopConfig.getIntegerList("potion.available-durations").stream().mapToInt(i -> i).toArray();
        if (durations.length == 0) durations = new int[]{30, 60, 300, 600, 1800, 3600};

        int durationIndex = 0;
        for (int i = 0; i < durations.length; i++) {
            if (durations[i] == durationSeconds) { durationIndex = i; break; }
        }

        int price = calculatePotionPrice(potionType, level, durationIndex);

        if (!checkAndDeductCoins(player, data, price, getPotionDisplayName(potionType))) return false;

        data.setPotionEffect(potionType, level, durationSeconds);
        applyPotionEffectToPlayer(player, potionType, level, durationSeconds);

        String msg = shopConfig.getString("messages.potion-purchased",
                "&a成功购买&e {potion} Lv.{level} &a时长&e {duration} &a花费&6 {price} 金币");
        msg = msg.replace("{potion}", getPotionDisplayName(potionType))
                 .replace("{level}", String.valueOf(level))
                 .replace("{duration}", formatDuration(durationSeconds))
                 .replace("{price}", MessageUtil.formatNumber(price));
        MessageUtil.sendMessage(player, msg);
        return true;
    }

    /**
     * 购买暴击率
     */
    public boolean buyCritRate(Player player, PlayerData data, double rateIncrease) {
        if (data.getCritHitRate() >= 1.0) {
            MessageUtil.sendMessage(player, shopConfig.getString("messages.crit-rate-full",
                    "&c爆率已满，无需再购买此道具。"));
            return false;
        }

        int price = calculateCritRatePrice(rateIncrease);

        if (!checkAndDeductCoins(player, data, price, "暴击率 +" + formatRateDisplay(rateIncrease))) return false;

        double newRate = data.getCritHitRate() + rateIncrease;
        if (newRate > 1.0) {
            // 溢出部分转换为经验
            double overflow = newRate - 1.0;
            int bonusExp = (int) Math.round(overflow * 500 * 100);
            data.addExp(bonusExp);
            data.setCritHitRate(1.0);
            String msg = shopConfig.getString("messages.crit-rate-overflow",
                    "&a暴击率已满！溢出部分已转换为&e {exp} &a经验值。");
            msg = msg.replace("{exp}", MessageUtil.formatNumber(bonusExp));
            MessageUtil.sendMessage(player, msg);
        } else {
            data.setCritHitRate(newRate);
        }

        String msg = shopConfig.getString("messages.crit-rate-purchased",
                "&a成功购买暴击率&e +{rate} &a当前暴击率: &e{current}% &a花费&6 {price} 金币");
        msg = msg.replace("{rate}", formatRateDisplay(rateIncrease))
                 .replace("{current}", String.format("%.1f", data.getCritHitRate() * 100))
                 .replace("{price}", MessageUtil.formatNumber(price));
        MessageUtil.sendMessage(player, msg);
        return true;
    }

    /**
     * 购买暴击倍率
     */
    public boolean buyCritMagnification(Player player, PlayerData data, double magIncrease) {
        int price = calculateCritMagPrice(magIncrease);

        if (!checkAndDeductCoins(player, data, price, "暴击倍率 +" + (int) magIncrease + "倍")) return false;

        // 叠加并刷新30分钟时长
        data.addBonusCritMagnification(magIncrease);
        int durationSeconds = shopConfig.getInt("crit-mag.duration-seconds", 1800); // 默认30分钟
        long newEndTime;
        if (data.getBonusCritMagEndTime() > System.currentTimeMillis()) {
            // 已有有效时长，叠加时长
            newEndTime = data.getBonusCritMagEndTime() + (durationSeconds * 1000L);
        } else {
            newEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
        data.setBonusCritMagEndTime(newEndTime);

        String msg = shopConfig.getString("messages.crit-mag-purchased",
                "&a成功购买暴击倍率&e +{mag}倍 &a当前额外倍率:&e {total}倍 &a剩余时间:&e {time} &a花费&6 {price} 金币");
        msg = msg.replace("{mag}", String.valueOf((int) magIncrease))
                 .replace("{total}", String.format("%.1f", data.getBonusCritMagnification()))
                 .replace("{time}", formatDuration(data.getCritMagRemainingSeconds()))
                 .replace("{price}", MessageUtil.formatNumber(price));
        MessageUtil.sendMessage(player, msg);
        return true;
    }

    /**
     * 购买等级
     */
    public boolean buyLevels(Player player, PlayerData data, int levelsToBuy) {
        int price = calculateLevelPrice(data, levelsToBuy);
        int targetLevel = data.getLevel() + levelsToBuy;

        if (!checkAndDeductCoins(player, data, price, "购买" + levelsToBuy + "级")) return false;

        // 直接升级
        int oldLevel = data.getLevel();
        int expNeeded = data.getExpToTargetLevel(targetLevel);
        // 清除当前经验并设置到目标等级
        data.setLevel(targetLevel);
        data.setExp(0);
        data.setLevelsInCurrentTier(data.getLevelsInCurrentTier() + levelsToBuy);

        String msg = shopConfig.getString("messages.level-purchased",
                "&a成功购买&e {levels} &a个等级！&7(&e{from}&7→&e{to}&7) &a花费&6 {price} 金币");
        msg = msg.replace("{levels}", String.valueOf(levelsToBuy))
                 .replace("{from}", String.valueOf(oldLevel))
                 .replace("{to}", String.valueOf(targetLevel))
                 .replace("{price}", MessageUtil.formatNumber(price));
        MessageUtil.sendMessage(player, msg);

        // 检查镐子升级
        plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
        return true;
    }

    // ==================== 连锁体验卡 ====================

    public int calculateChainCardPrice(String cardType, int blocks) {
        double basePrice = shopConfig.getDouble("chain-card." + cardType + ".base-price", 8000);
        double tierMult = shopConfig.getDouble("chain-card." + cardType + ".tier-multiplier", 3.0);
        return safePrice(basePrice * Math.pow(tierMult, blocks - 1));
    }

    public boolean buyChainCard(Player player, PlayerData data, String cardType, int blocks, int height) {
        int price = calculateChainCardPrice(cardType, blocks);
        String displayName = getChainCardDisplayName(cardType);
        int duration = shopConfig.getInt("chain-card.duration-seconds", 30);
        int absMax = 15;

        if (!checkAndDeductCoins(player, data, price, displayName + " x" + blocks + " 高" + height)) return false;

        String existingType = data.getChainCardType();
        boolean hasExisting = data.hasChainCard();

        if (hasExisting && existingType != null && existingType.equals(cardType)) {
            // 同类型：叠加方块数+高度+时长
            int currentBlocks = data.getChainCardBlocks();
            int currentHeight = data.getChainCardHeight();
            int newBlocks = currentBlocks + blocks;
            int newHeight = currentHeight + height;

            if (newBlocks > absMax) {
                data.setChainCardBlocks(absMax);
            } else {
                data.setChainCardBlocks(newBlocks);
            }

            if (newHeight > absMax) {
                if (currentHeight >= absMax) {
                    MessageUtil.sendMessage(player, shopConfig.getString("messages.chain-card-height-full",
                            "&c高度已满，无法购买。"));
                    // 退还金币
                    data.addCoins(price);
                    return false;
                }
                data.setChainCardHeight(absMax);
            } else {
                data.setChainCardHeight(newHeight);
            }

            data.setChainCardEndTime(data.getChainCardEndTime() + (duration * 1000L));
            String msg = shopConfig.getString("messages.chain-card-extended",
                    "&a已叠加&e {card} &a时长，当前剩余&e {time}");
            msg = msg.replace("{card}", displayName + " x" + data.getChainCardBlocks() + " 高" + data.getChainCardHeight())
                     .replace("{time}", formatDuration(data.getChainCardRemainingSeconds()));
            MessageUtil.sendMessage(player, msg);
        } else {
            if (hasExisting) {
                String overwriteMsg = shopConfig.getString("messages.chain-card-overwritten",
                        "&e已切换为新的连锁类型&e {card}&e，旧效果已覆盖（不返还金币）");
                overwriteMsg = overwriteMsg.replace("{card}", displayName);
                MessageUtil.sendMessage(player, overwriteMsg);
            }
            data.setChainCardType(cardType);
            data.setChainCardBlocks(blocks);
            data.setChainCardHeight(height);
            data.setChainCardEndTime(System.currentTimeMillis() + (duration * 1000L));
            String msg = shopConfig.getString("messages.chain-card-purchased",
                    "&a成功购买&e {card} &a范围&e {blocks} &a高度&e {height} &a时长&e 30秒 &a花费&6 {price} 金币");
            msg = msg.replace("{card}", displayName)
                     .replace("{blocks}", String.valueOf(blocks))
                     .replace("{height}", String.valueOf(height))
                     .replace("{price}", MessageUtil.formatNumber(price));
            MessageUtil.sendMessage(player, msg);
        }
        return true;
    }

    /**
     * 购买一般连锁（全地图生效，3小时）
     */
    public boolean buyGlobalChain(Player player, PlayerData data) {
        int price = shopConfig.getInt("global-chain.price", 100);
        int duration = shopConfig.getInt("global-chain.duration-seconds", 10800);

        if (!checkAndDeductCoins(player, data, price, "一般连锁")) return false;

        long newEnd;
        if (data.hasGlobalChain()) {
            newEnd = data.getGlobalChainEndTime() + (duration * 1000L);
        } else {
            newEnd = System.currentTimeMillis() + (duration * 1000L);
        }
        data.setGlobalChainEndTime(newEnd);

        String msg = shopConfig.getString("messages.global-chain-purchased",
                "&a成功购买&d一般连锁&a！&7(&e所有世界生效&7) &a花费&6 100 金币");
        MessageUtil.sendMessage(player, msg);
        return true;
    }

    public static String getChainCardDisplayName(String cardType) {
        return switch (cardType) {
            case "plane_x" -> "平面X轴连锁";
            case "plane_z" -> "平面Z轴连锁";
            case "radius" -> "半径连锁";
            case "ray" -> "视角方向连锁";
            default -> cardType;
        };
    }

    public int getChainCardMaxBlocks(String cardType) {
        return 15; // 统一上限15
    }

    // ==================== 工具方法 ====================

    public static String getPotionDisplayName(String potionType) {
        return switch (potionType.toLowerCase()) {
            case "haste" -> "急迫";
            case "speed" -> "速度";
            case "luck" -> "幸运";
            default -> potionType;
        };
    }

    public static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        return (seconds / 3600) + "小时";
    }

    public static String formatRateDisplay(double rate) {
        if (rate < 0.01) return String.format("%.1f%%", rate * 100);
        return String.format("%.0f%%", rate * 100);
    }

    public void applyPotionEffectToPlayer(Player player, String potionType, int level, int durationSeconds) {
        var type = switch (potionType.toLowerCase()) {
            case "haste" -> org.bukkit.potion.PotionEffectType.HASTE;
            case "speed" -> org.bukkit.potion.PotionEffectType.SPEED;
            case "luck" -> org.bukkit.potion.PotionEffectType.LUCK;
            default -> null;
        };
        if (type == null) return;

        // 先移除旧效果，再应用新效果
        player.removePotionEffect(type);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                type, durationSeconds * 20, level - 1, false, false, true));
    }

    /**
     * 重新应用所有有效药水效果
     */
    public void reapplyPotionEffects(Player player, PlayerData data) {
        data.cleanExpiredEffects();
        for (var entry : data.getActivePotionEffects().entrySet()) {
            var effectData = entry.getValue();
            int remainingSeconds = effectData.getRemainingSeconds();
            if (remainingSeconds > 0) {
                applyPotionEffectToPlayer(player, entry.getKey(), effectData.level, remainingSeconds);
            }
        }
    }

    /**
     * 获取可用暴击率档位
     */
    public List<Double> getCritRateTiers() {
        return List.of(0.005, 0.01, 0.05, 0.10, 0.50);
    }

    /**
     * 获取可用暴击倍率档位
     */
    public List<Double> getCritMagTiers() {
        return List.of(1.0, 5.0, 8.0, 10.0, 15.0, 20.0);
    }

    /**
     * 获取药水可用时长
     */
    public List<Integer> getPotionDurations() {
        List<Integer> list = shopConfig.getIntegerList("potion.available-durations");
        if (list.isEmpty()) {
            return List.of(30, 60, 300, 600, 1800, 3600);
        }
        return list;
    }

    /**
     * 获取最大药水等级
     */
    public int getMaxPotionLevel() {
        return shopConfig.getInt("potion.max-level", 30);
    }

    /**
     * 设置商店价格（管理员命令）
     */
    public boolean setShopPrice(String itemKey, int price) {
        if (!shopConfig.contains(itemKey)) {
            return false;
        }
        shopConfig.set(itemKey, price);
        saveShopConfig();
        return true;
    }
}
