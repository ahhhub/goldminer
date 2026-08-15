package com.godminer.listener;

import com.godminer.GoldMiner;
import com.godminer.model.Mineral;
import com.godminer.model.MineralRarity;
import com.godminer.model.PickaxeTier;
import com.godminer.model.PlayerData;
import com.godminer.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 方块事件监听器
 */
public class BlockListener implements Listener {

    private final GoldMiner plugin;

    public BlockListener(GoldMiner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // 检查是否在矿场世界
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        // 检查方块是否在矿场区域内
        if (!isInMineArea(block.getLocation())) {
            return;
        }

        // 检查是否是定义的矿物方块
        Material blockType = block.getType();
        Mineral mineral = findMineral(blockType);
        if (mineral == null) {
            return;
        }

        // 矿场矿物：取消掉落，给予积分
        event.setDropItems(false);
        event.setExpToDrop(0);

        // 收集所有需处理的方块（主方块 + 连锁方块）
        List<Block> allBlocks = new ArrayList<>();
        allBlocks.add(block);
        allBlocks.addAll(getChainCardBlocks(player, data, block));

        // 逐方块独立暴击判定，汇总统计
        double critRate = data.getCritHitRate();
        double critMag = data.getEffectiveCritMagnification();
        int totalCoins = 0;
        int totalExp = 0;
        int critCount = 0;
        int critBonusCoins = 0;
        int critBonusExp = 0;

        for (Block b : allBlocks) {
            if (!isInMineArea(b.getLocation())) continue;
            Mineral m = findMineral(b.getType());
            if (m == null) continue;

            int baseCoin = m.getCoinReward();
            int baseExp = m.getExpReward();

            // 独立暴击判定
            boolean blockCrit = (critRate > 0 && ThreadLocalRandom.current().nextDouble() < critRate);
            int finalCoin = baseCoin;
            int finalExp = baseExp;

            if (blockCrit) {
                critCount++;
                int bonusCoin = (int) Math.round(baseCoin * critMag);
                int bonusExp = (int) Math.round(baseExp * critMag);
                finalCoin += bonusCoin;
                finalExp += bonusExp;
                critBonusCoins += bonusCoin;
                critBonusExp += bonusExp;
            }

            totalCoins += finalCoin;
            totalExp += finalExp;
            data.addCoins(finalCoin);
            data.addExp(finalExp);
            if (b != block) b.setType(Material.AIR, false); // 连锁方块清除，主方块由原版处理
        }

        boolean anyCrit = (critCount > 0);
        int totalBlocks = allBlocks.size();

        // 显示汇总标题
        String titleText;
        if (anyCrit) {
            titleText = MessageUtil.colorizeString(
                    "&c&l暴击 x" + critCount + "! &6+" + MessageUtil.formatNumber(totalCoins)
                    + " ⛁ &7| &a+" + totalExp + " ✦ &7(暴击+" + MessageUtil.formatNumber(critBonusCoins) + "⛁)");
        } else {
            titleText = MessageUtil.colorizeString(
                    "&6+" + MessageUtil.formatNumber(totalCoins) + " ⛁ &7| &a+" + totalExp + " ✦");
        }
        player.showTitle(Title.title(
                Component.empty(),
                MessageUtil.colorize(titleText),
                Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(1500),
                        Duration.ofMillis(500)
                )
        ));

        // 发送ActionBar消息
        player.sendActionBar(MessageUtil.colorize(
                plugin.getLangConfig().getString("mining.coin-earned", "&6+{coin} 金币 &7| &a+{exp} 经验")
                        .replace("{coin}", MessageUtil.formatNumber(totalCoins))
                        .replace("{exp}", String.valueOf(totalExp))
        ));

        // 检查升级
        checkLevelUp(player, data);
    }

    /**
     * 收集连锁卡方块（仅收集，不计算奖励）
     */
    private List<Block> getChainCardBlocks(Player player, PlayerData data, Block originBlock) {
        if (!data.hasChainCard()) return Collections.emptyList();

        String cardType = data.getChainCardType();
        int blocks = data.getChainCardBlocks();
        int height = data.getChainCardHeight();
        var facing = player.getFacing();
        List<Block> result = new ArrayList<>();

        switch (cardType) {
            case "plane_x" -> {
                for (int x = -blocks; x <= blocks; x++) {
                    for (int y = 0; y < height; y++) {
                        if (x == 0 && y == 0) continue;
                        Block b = originBlock.getRelative(x, y, 0);
                        if (isInMineArea(b.getLocation())) result.add(b);
                    }
                }
            }
            case "plane_z" -> {
                for (int z = -blocks; z <= blocks; z++) {
                    for (int y = 0; y < height; y++) {
                        if (z == 0 && y == 0) continue;
                        Block b = originBlock.getRelative(0, y, z);
                        if (isInMineArea(b.getLocation())) result.add(b);
                    }
                }
            }
            case "radius" -> {
                for (int x = -blocks; x <= blocks; x++) {
                    for (int y = 0; y < height; y++) {
                        for (int z = -blocks; z <= blocks; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            Block b = originBlock.getRelative(x, y, z);
                            if (isInMineArea(b.getLocation())) result.add(b);
                        }
                    }
                }
            }
            case "ray" -> {
                int rayBlocks = data.getChainCardBlocks();
                for (int i = 1; i <= rayBlocks; i++) {
                    for (int y = 0; y < height; y++) {
                        Block b = switch (facing) {
                            case NORTH -> originBlock.getRelative(0, y, -i);
                            case SOUTH -> originBlock.getRelative(0, y, i);
                            case EAST -> originBlock.getRelative(i, y, 0);
                            case WEST -> originBlock.getRelative(-i, y, 0);
                            case UP -> originBlock.getRelative(0, i + y, 0);
                            case DOWN -> originBlock.getRelative(0, -(i + y), 0);
                            default -> originBlock.getRelative(0, y, i);
                        };
                        if (isInMineArea(b.getLocation())) result.add(b);
                    }
                }
            }
        }
        return result;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // 矿场内允许放置所有方块（不再限制）
    }

    // ==================== 一般连锁 ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlobalChainBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;
        if (!data.hasGlobalChain()) return;

        // 矿场世界内不触发一般连锁
        if (data.getCurrentMineWorld() != null && player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        Material type = block.getType();
        // 只连锁矿物和木头类方块
        if (!isChainableBlock(type)) return;

        // 连锁同类型方块（9x9x3范围）
        List<Block> chainBlocks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Block b = block.getRelative(x, y, z);
                    if (b.getType() == type) chainBlocks.add(b);
                }
            }
        }

        // 使用工具附魔处理掉落（Fortune等）
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block b : chainBlocks) {
            // 模拟原版挖掘，应用附魔
            b.breakNaturally(tool);
        }
    }

    private boolean isChainableBlock(Material mat) {
        String name = mat.name();
        return name.contains("_ORE") || name.contains("_LOG") || name.contains("_WOOD")
                || name.contains("DEEPSLATE") && name.contains("_ORE")
                || name.contains("NETHERRACK") && name.contains("_ORE")
                || name.contains("ANCIENT_DEBRIS")
                || name.contains("COAL") || name.contains("COPPER") || name.contains("IRON")
                || name.contains("GOLD") && !name.equals("GOLD_BLOCK") && !name.equals("GOLD_INGOT")
                || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("LAPIS")
                || name.contains("REDSTONE") && !name.equals("REDSTONE_BLOCK")
                || name.contains("NETHER_GOLD")
                || mat == Material.OBSIDIAN || mat == Material.CRYING_OBSIDIAN
                || mat == Material.GLOWSTONE;
    }

    /**
     * 根据Material查找矿物定义
     */
    private Mineral findMineral(Material material) {
        for (MineralRarity rarity : MineralRarity.values()) {
            for (Mineral mineral : plugin.getMineralManager().getMineralsByRarity(rarity)) {
                if (mineral.getMaterial() == material) {
                    return mineral;
                }
            }
        }
        return null;
    }

    /**
     * 判断位置是否在矿场区域内
     */
    private boolean isInMineArea(Location loc) {
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;
        return Math.abs(loc.getBlockX()) <= halfSize
                && loc.getBlockY() >= 0 && loc.getBlockY() < centerSize
                && Math.abs(loc.getBlockZ()) <= halfSize;
    }

    /**
     * 检查并处理升级
     */
    private void checkLevelUp(Player player, PlayerData data) {
        boolean leveled = false;
        int maxIterations = 100; // 防止无限循环
        int iteration = 0;

        while (data.canLevelUp() && iteration < maxIterations) {
            data.levelUp();
            leveled = true;
            iteration++;
        }

        if (leveled) {
            // 发送升级消息
            String msg = plugin.getLangConfig().getString("mining.level-up",
                    "&a恭喜！你的矿工等级提升到了 &e{level} &a级！");
            msg = MessageUtil.replacePlaceholders(msg, "{level}", String.valueOf(data.getLevel()));
            MessageUtil.sendMessage(player, msg);

            // 播放升级音效
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // 检查镐子升级
            String upgradeResult = plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
            if (upgradeResult != null) {
                String upgradeMsg = plugin.getLangConfig().getString("mining." + upgradeResult, "&a你的镐子已升级！");
                upgradeMsg = MessageUtil.replacePlaceholders(upgradeMsg, "{pickaxe}",
                        plugin.getLangConfig().getString("pickaxe." + data.getPickaxeTier().getConfigKey(), "镐"));
                MessageUtil.sendMessage(player, upgradeMsg);

                // 更新镐子
                updatePlayerPickaxe(player, data);

                // 同步升级装备
                if (data.isSuitVisible()) {
                    plugin.getPickaxeManager().equipArmor(player, data);
                }
            }

            // 更新BossBar
            updateBossBar(player);
        }
    }

    /**
     * 更新玩家快捷栏中的镐子
     */
    private void updatePlayerPickaxe(Player player, PlayerData data) {
        // 移除旧的镐子
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (item != null && isGoldMinerPickaxe(item)) {
                inv.setItem(i, null);
                break;
            }
        }

        // 给予新镐子
        inv.setItem(0, plugin.getPickaxeManager().createPickaxe(data));
    }

    /**
     * 判断是否是矿场镐子
     */
    private boolean isGoldMinerPickaxe(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().isUnbreakable() &&
                (item.getType().toString().endsWith("_PICKAXE"));
    }

    /**
     * 更新BossBar
     */
    private void updateBossBar(Player player) {
        // BossBar 处理在主类中
        plugin.getBossBarManager().updateBossBar(player);
    }
}
