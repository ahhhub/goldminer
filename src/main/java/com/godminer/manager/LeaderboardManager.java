package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 排行榜管理器
 */
public class LeaderboardManager {

    private final GoldMiner plugin;

    public LeaderboardManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示排行榜（前5名）
     */
    public void showLeaderboard(Player viewer) {
        Map<UUID, PlayerData> allData = plugin.getPlayerDataManager().getAllPlayerData();

        // 按金币降序，然后等级降序排序
        List<PlayerData> sorted = allData.values().stream()
                .sorted(Comparator.comparingInt(PlayerData::getCoins).reversed()
                        .thenComparingInt(PlayerData::getLevel).reversed())
                .limit(5)
                .collect(Collectors.toList());

        String header = plugin.getLangConfig().getString("leaderboard.header", "&6===== 矿场排行榜 =====");
        MessageUtil.sendMessage(viewer, header);

        if (sorted.isEmpty()) {
            MessageUtil.sendMessage(viewer,
                    plugin.getLangConfig().getString("leaderboard.empty", "&7暂无排行数据。"));
            return;
        }

        int rank = 1;
        for (PlayerData data : sorted) {
            String name = data.getPlayerName();
            if (name == null) {
                name = Bukkit.getOfflinePlayer(data.getUuid()).getName();
                if (name == null) name = "Unknown";
            }

            String entry = plugin.getLangConfig().getString("leaderboard.entry",
                    "&e{index}. &b{player} &7- 金币: &6{gold} &7- 等级: &a{level}");
            entry = MessageUtil.replacePlaceholders(entry,
                    "{index}", String.valueOf(rank),
                    "{player}", name,
                    "{gold}", MessageUtil.formatNumber(data.getCoins()),
                    "{level}", String.valueOf(data.getLevel()));
            MessageUtil.sendMessage(viewer, entry);
            rank++;
        }
    }
}
