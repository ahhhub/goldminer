package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器 - 管理内存中的玩家数据
 */
public class PlayerDataManager {

    private final GoldMiner plugin;
    private final Map<UUID, PlayerData> playerDataMap;

    public PlayerDataManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    /**
     * 加载所有玩家数据
     */
    public void loadAll() {
        Map<UUID, PlayerData> loaded = plugin.getDatabaseManager().loadAllPlayers();
        playerDataMap.putAll(loaded);
        plugin.getLogger().info("已加载 " + loaded.size() + " 个玩家数据。");
    }

    /**
     * 获取玩家数据（如果不存在则创建）
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, k -> {
            PlayerData data = plugin.getDatabaseManager().loadPlayer(uuid);
            if (data == null) {
                data = new PlayerData(uuid);
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                data.setPlayerName(name);
            }
            return data;
        });
    }

    /**
     * 获取所有玩家数据
     */
    public Map<UUID, PlayerData> getAllPlayerData() {
        return Collections.unmodifiableMap(playerDataMap);
    }

    /**
     * 保存玩家数据
     */
    public void savePlayer(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            plugin.getDatabaseManager().savePlayer(data);
        }
    }

    /**
     * 保存所有在线玩家数据
     */
    public void saveAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player.getUniqueId());
        }
    }

    /**
     * 保存所有玩家数据
     */
    public void saveAll() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayer(uuid);
        }
        plugin.getLogger().info("所有玩家数据已保存。");
    }

    /**
     * 检查玩家是否在矿场中
     */
    public boolean isInMine(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        return data != null && data.getCurrentMineWorld() != null;
    }

    /**
     * 获取玩家的矿场世界名
     */
    public String getMineWorld(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        return data != null ? data.getCurrentMineWorld() : null;
    }

    /**
     * 设置玩家矿场世界
     */
    public void setMineWorld(UUID uuid, String worldName) {
        PlayerData data = getPlayerData(uuid);
        data.setCurrentMineWorld(worldName);
    }

    /**
     * 清理玩家矿场状态
     */
    public void clearMineWorld(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            data.setCurrentMineWorld(null);
        }
    }
}
