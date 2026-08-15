package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.Mineral;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 矿场管理器 - 负责地面矿场的生成和刷新
 * 矿场从 y=0 开始向上生长，玩家在矿场顶部安全区域出生
 */
public class MineManager {

    private final GoldMiner plugin;
    private final Map<String, BukkitTask> refreshTasks;
    private final Map<String, Set<Location>> mineBlocks;
    private final Map<String, Location> mineCenterCache;
    private final Map<String, Integer> refreshCountdowns; // 世界名 -> 剩余秒数
    private final Map<String, Integer> refreshIntervals;   // 世界名 -> 刷新间隔

    public MineManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.refreshTasks = new HashMap<>();
        this.mineBlocks = new HashMap<>();
        this.mineCenterCache = new HashMap<>();
        this.refreshCountdowns = new HashMap<>();
        this.refreshIntervals = new HashMap<>();
    }

    /**
     * 生成地面上的初始矿场 (从 y=0 到 y=centerSize)
     */
    public void generateInitialMine(World world) {
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;
        String worldName = world.getName();
        Set<Location> blocks = ConcurrentHashMap.newKeySet();

        // 从 y=0 开始在地面上生成矿场立方体
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int y = 0; y < centerSize; y++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    Material blockType = rollBlock();
                    Location loc = new Location(world, x, y, z);
                    loc.getBlock().setType(blockType, false);
                    if (blockType != Material.AIR) {
                        blocks.add(loc);
                    }
                }
            }
        }

        // 在顶部创建安全平台
        int platformY = centerSize;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, platformY, z).setType(Material.BEDROCK);
                world.getBlockAt(x, platformY + 1, z).setType(Material.AIR);
                world.getBlockAt(x, platformY + 2, z).setType(Material.AIR);
            }
        }

        mineBlocks.put(worldName, blocks);
        mineCenterCache.put(worldName, new Location(world, 0.5, platformY + 1, 0.5));
        plugin.getLogger().info("矿场世界 " + worldName + " 地面矿场已生成，方块数: " + blocks.size());
    }

    private Material rollBlock() {
        Mineral mineral = plugin.getMineralManager().rollRandomMineral();
        return mineral != null ? mineral.getMaterial() : Material.STONE;
    }

    /**
     * 开始矿场自动刷新（每秒更新倒计时）
     */
    public void startRefreshTask(World world) {
        int interval = plugin.getConfig().getInt("mine.refresh-interval", 30);
        String worldName = world.getName();
        stopRefreshTask(worldName);

        refreshIntervals.put(worldName, interval);
        refreshCountdowns.put(worldName, interval);

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                int countdown = interval - (tick % interval);
                if (countdown == 0) countdown = interval;
                refreshCountdowns.put(worldName, countdown);

                // 每秒更新BossBar
                plugin.getBossBarManager().updateAllInWorld(worldName);

                // 到达刷新时间
                if (tick % interval == 0) {
                    checkAndRefreshMine(world);
                    refreshCountdowns.put(worldName, interval);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒执行

        refreshTasks.put(worldName, task);
        plugin.getLogger().info("矿场自动刷新已启动，间隔: " + interval + "秒");
    }

    /**
     * 获取刷新倒计时（秒）
     */
    public int getRefreshCountdown(String worldName) {
        return refreshCountdowns.getOrDefault(worldName, 0);
    }

    /**
     * 获取刷新进度 (0.0 ~ 1.0)
     */
    public float getRefreshProgress(String worldName) {
        int interval = refreshIntervals.getOrDefault(worldName, 30);
        int countdown = refreshCountdowns.getOrDefault(worldName, interval);
        return Math.max(0f, Math.min(1f, (float)(interval - countdown) / interval));
    }

    /**
     * 检查玩家位置安全并刷新矿场（先刷新再检查安全）
     */
    private void checkAndRefreshMine(World world) {
        // 先执行刷新
        refreshMine(world);

        // 刷新后检查每个玩家是否被卡在方块中
        Location safeLoc = getSafeLocation(world);
        for (Player player : world.getPlayers()) {
            if (!isPlayerSafe(player)) {
                player.teleport(safeLoc);
                String msg = plugin.getLangConfig().getString("mine.unsafe-teleport",
                        "&c检测到你在危险区域，已将你传送到安全区域！");
                com.godminer.util.MessageUtil.sendMessage(player, msg);
            }
        }
    }

    /**
     * 判断玩家位置是否安全（头脚两格必须有空气）
     */
    private boolean isPlayerSafe(Player player) {
        Location loc = player.getLocation();
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;

        // 玩家在矿场范围外，安全
        if (Math.abs(loc.getBlockX()) > halfSize + 5 || Math.abs(loc.getBlockZ()) > halfSize + 5) {
            return true;
        }

        // 玩家在矿场高度范围外，安全
        if (loc.getBlockY() < 0 || loc.getBlockY() > centerSize + 10) {
            return true;
        }

        // 检查脚部和头部是否有固体方块
        Location feetLoc = loc.clone();
        Location headLoc = loc.clone().add(0, 1, 0);

        Material feetBlock = feetLoc.getBlock().getType();
        Material headBlock = headLoc.getBlock().getType();

        // 如果脚或头被固体方块占据 → 不安全
        if (feetBlock.isSolid() && feetBlock != Material.GLASS) {
            return false;
        }
        if (headBlock.isSolid() && headBlock != Material.GLASS) {
            return false;
        }

        return true;
    }

    /**
     * 获取矿场安全位置
     */
    public Location getSafeLocation(World world) {
        Location cached = mineCenterCache.get(world.getName());
        if (cached != null) {
            return cached.clone();
        }
        return plugin.getWorldManager().getSafeSpawnLocation(world);
    }

    public void stopRefreshTask(String worldName) {
        BukkitTask task = refreshTasks.remove(worldName);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 刷新矿场 - 完全重新随机生成所有方块
     */
    public void refreshMine(World world) {
        String worldName = world.getName();
        Set<Location> blocks = mineBlocks.get(worldName);

        // 如果方块列表为空（如服务器重启后），重建追踪列表
        if (blocks == null || blocks.isEmpty()) {
            blocks = rebuildMineBlockList(world);
            if (blocks.isEmpty()) {
                plugin.getLogger().warning("矿场 " + worldName + " 无有效方块，请先执行 /goldminer join 生成矿场！");
                return;
            }
        }

        int refreshed = 0;
        for (Location loc : blocks) {
            Material newType = rollBlock();
            loc.getBlock().setType(newType, false);
            refreshed++;
        }

        if (refreshed > 0) {
            plugin.getLogger().info("矿场 " + worldName + " 已刷新 " + refreshed + " 个方块。");
        }
    }

    /**
     * 重建矿场方块追踪列表（服务器重启后恢复，不改变方块）
     */
    private Set<Location> rebuildMineBlockList(World world) {
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;
        String worldName = world.getName();
        Set<Location> blocks = ConcurrentHashMap.newKeySet();

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int y = 0; y < centerSize; y++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    Location loc = new Location(world, x, y, z);
                    if (loc.getBlock().getType() != Material.AIR) {
                        blocks.add(loc);
                    }
                }
            }
        }

        mineBlocks.put(worldName, blocks);
        plugin.getLogger().info("矿场 " + worldName + " 方块追踪列表已重建，方块数: " + blocks.size());
        return blocks;
    }

    public void registerMineBlock(Location loc) {
        String worldName = loc.getWorld().getName();
        Set<Location> blocks = mineBlocks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        blocks.add(loc);
    }

    public void unregisterMineBlock(Location loc) {
        String worldName = loc.getWorld().getName();
        Set<Location> blocks = mineBlocks.get(worldName);
        if (blocks != null) {
            blocks.remove(loc);
        }
    }

    public boolean isInMine(Location loc) {
        String worldName = loc.getWorld().getName();
        Set<Location> blocks = mineBlocks.get(worldName);
        if (blocks == null) return false;
        return blocks.contains(loc);
    }

    public void cleanupWorld(String worldName) {
        stopRefreshTask(worldName);
        mineBlocks.remove(worldName);
        mineCenterCache.remove(worldName);
        refreshCountdowns.remove(worldName);
        refreshIntervals.remove(worldName);
    }

    public void shutdown() {
        refreshTasks.values().forEach(BukkitTask::cancel);
        refreshTasks.clear();
        mineBlocks.clear();
        mineCenterCache.clear();
        refreshCountdowns.clear();
        refreshIntervals.clear();
    }
}
