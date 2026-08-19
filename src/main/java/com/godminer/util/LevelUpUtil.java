package com.godminer.util;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 升级处理工具 - 集中处理经验升级与等级提升带来的连锁反应
 * （升级消息、镐子升级、装备同步、BossBar 更新）
 */
public final class LevelUpUtil {

    private LevelUpUtil() {}

    /**
     * 经验变化后处理升级（可能连升多级）
     */
    public static void handleLevelUp(GoldMiner plugin, Player player, PlayerData data) {
        boolean leveled = false;
        int iteration = 0;
        while (data.canLevelUp() && iteration < 100) {
            data.levelUp();
            leveled = true;
            iteration++;
        }
        if (!leveled) return;

        String msg = plugin.getLangConfig().getString("mining.level-up",
                "&a恭喜！你的矿工等级提升到了 &e{level} &a级！");
        msg = MessageUtil.replacePlaceholders(msg, "{level}", String.valueOf(data.getLevel()));
        MessageUtil.sendMessage(player, msg);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        processPickaxeUpgrade(plugin, player, data);
        plugin.getBossBarManager().updateBossBar(player);
    }

    /**
     * 直接提升矿工等级（等级升级球）
     */
    public static void applyLevels(GoldMiner plugin, Player player, PlayerData data, int levels) {
        data.setLevel(data.getLevel() + levels);
        processPickaxeUpgrade(plugin, player, data);
        plugin.getBossBarManager().updateBossBar(player);
    }

    /**
     * 检查并处理镐子升级（附魔升级 / 材质晋升），同步玩家装备
     */
    private static void processPickaxeUpgrade(GoldMiner plugin, Player player, PlayerData data) {
        String upgradeResult = plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
        if (upgradeResult == null) return;

        String upgradeMsg = plugin.getLangConfig().getString("mining." + upgradeResult, "&a你的镐子已升级！");
        upgradeMsg = MessageUtil.replacePlaceholders(upgradeMsg, "{pickaxe}",
                plugin.getLangConfig().getString("pickaxe." + data.getPickaxeTier().getConfigKey(), "镐"));
        MessageUtil.sendMessage(player, upgradeMsg);

        replacePickaxe(plugin, player, data);

        if (data.isSuitVisible()) {
            plugin.getPickaxeManager().equipArmor(player, data);
        }
    }

    /**
     * 替换玩家背包中的旧镐子
     */
    private static void replacePickaxe(GoldMiner plugin, Player player, PlayerData data) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getItemMeta() != null
                    && item.getItemMeta().isUnbreakable()
                    && item.getType().toString().endsWith("_PICKAXE")) {
                inv.setItem(i, null);
                break;
            }
        }
        inv.setItem(0, plugin.getPickaxeManager().createPickaxe(data));
    }
}
