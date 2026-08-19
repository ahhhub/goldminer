package com.godminer.listener;

import com.godminer.GoldMiner;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 特殊物品监听器 - 处理经验瓶与等级升级球的右键使用
 * 带有特殊 NBT 的经验瓶/雪球右键即使用（不抛出），
 * 原版无 NBT 的同类物品保持原版行为。
 */
public class SpecialItemListener implements Listener {

    private final GoldMiner plugin;

    public SpecialItemListener(GoldMiner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        Player player = event.getPlayer();
        var manager = plugin.getSpecialItemManager();

        if (item.getType() == Material.EXPERIENCE_BOTTLE) {
            Integer exp = manager.getExpBottleAmount(item);
            if (exp == null) return; // 原版经验瓶
            event.setCancelled(true);
            manager.useExpBottle(player, item, exp);
        } else if (item.getType() == Material.SNOWBALL) {
            Integer levels = manager.getLevelBallAmount(item);
            if (levels == null) return; // 原版雪球
            event.setCancelled(true);
            manager.useLevelBall(player, item, levels);
        }
    }
}
