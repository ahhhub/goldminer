package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.MessageUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossBar管理器 - 以血量条样式显示经验值进度
 */
public class BossBarManager {

    private final GoldMiner plugin;
    private final Map<UUID, BossBar> playerBossBars;

    public BossBarManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.playerBossBars = new ConcurrentHashMap<>();
    }

    /**
     * 更新玩家的BossBar - 血量扣除样式 + 经验值文本
     */
    public void updateBossBar(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;

        BossBar existing = playerBossBars.get(player.getUniqueId());

        int level = data.getLevel();
        int exp = data.getExp();
        int expToNext = data.getExpToNextLevel();
        float progress = Math.min(1.0f, Math.max(0f, (float) exp / expToNext));

        String barFormat = plugin.getLangConfig().getString("bossbar.format",
                "&6矿工等级: &e{level} &7| &a经验值: &e{exp}&7/&e{max}");
        String title = barFormat
                .replace("{level}", String.valueOf(level))
                .replace("{exp}", String.valueOf(exp))
                .replace("{max}", String.valueOf(expToNext));

        Component component = MessageUtil.colorize(title);

        if (existing == null) {
            BossBar bossBar = BossBar.bossBar(component, progress, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);
            player.showBossBar(bossBar);
            playerBossBars.put(player.getUniqueId(), bossBar);
        } else {
            existing.name(component);
            existing.progress(progress);
        }
    }

    /**
     * 移除玩家的BossBar
     */
    public void removeBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    /**
     * 清理所有BossBar
     */
    public void cleanup() {
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerBossBars.clear();
    }

    /**
     * 批量更新矿场中所有玩家的BossBar
     */
    public void updateAllInWorld(String worldName) {
        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null && player.getWorld().getName().equals(worldName)) {
                updateBossBar(player);
            }
        }
    }
}
