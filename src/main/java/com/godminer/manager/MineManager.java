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

/**
 * 矿场管理器 - 负责地面矿场的生成和刷新
 * 矿场从 y=0 开始向上生长，玩家在矿场顶部安全区域出生
 */
public class MineManager {

    private final GoldMiner plugin;
    private final Map<String, BukkitTask> refreshTasks;
    private final Map<String, BukkitTask> refreshBatchTasks; // 分批刷新任务
    private final Map<String, Location> mineCenterCache;
    private final Map<String, Integer> refreshCountdowns; // 世界名 -> 剩余秒数
    private final Map<String, Integer> refreshIntervals;   // 世界名 -> 刷新间隔

    public MineManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.refreshTasks = new HashMap<>();
        this.refreshBatchTasks = new HashMap<>();
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

        // 从 y=0 开始在地面上生成矿场立方体
        int generated = 0;
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int y = 0; y < centerSize; y++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    Material blockType = rollBlock();
                    world.getBlockAt(x, y, z).setType(blockType, false);
                    generated++;
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

        mineCenterCache.put(worldName, new Location(world, 0.5, platformY + 1, 0.5));
        plugin.getLogger().info("矿场世界 " + worldName + " 地面矿场已生成，方块数: " + generated);
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
     * 检查玩家位置安全并刷新矿场
     */
    private void checkAndRefreshMine(World world) {
        // 分批刷新（完成时自动执行安全检查）
        refreshMine(world);
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
     * 刷新完成后传送被卡住的玩家
     */
    private void checkPlayerSafety(World world) {
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
     * 刷新矿场 - 分批重新随机生成整个矿场立方体的所有方块
     * 每 tick 处理一批（默认20000块），避免主线程一次性处理百万方块造成卡顿
     * 刷新完成后自动检查玩家安全
     */
    public void refreshMine(World world) {
        String worldName = world.getName();

        // 取消正在进行的批次
        BukkitTask old = refreshBatchTasks.remove(worldName);
        if (old != null) old.cancel();

        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;
        int xSize = halfSize * 2 + 1;
        int zSize = halfSize * 2 + 1;
        int total = xSize * centerSize * zSize;
        int batchSize = Math.max(1000, plugin.getConfig().getInt("mine.refresh-batch-size", 20000));

        final int[] cursor = {0};
        final int fHalf = halfSize, fXSize = xSize, fZSize = zSize, fTotal = total, fCenterSize = centerSize;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (cursor[0] < fTotal && processed < batchSize) {
                    int idx = cursor[0]++;
                    int y = idx / (fXSize * fZSize);
                    int rem = idx % (fXSize * fZSize);
                    int zOff = rem / fXSize;
                    int xOff = rem % fXSize;
                    world.getBlockAt(-fHalf + xOff, y, -fHalf + zOff).setType(rollBlock(), false);
                    processed++;
                }

                if (cursor[0] >= fTotal) {
                    refreshBatchTasks.remove(worldName);
                    plugin.getLogger().info("矿场 " + worldName + " 已刷新完成，共 " + fTotal + " 个方块。");
                    checkPlayerSafety(world);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        refreshBatchTasks.put(worldName, task);
        plugin.getLogger().info("矿场 " + worldName + " 开始分批刷新，共 " + total + " 方块，每tick " + batchSize + " 块。");
    }

    public void cleanupWorld(String worldName) {
        stopRefreshTask(worldName);
        BukkitTask batchTask = refreshBatchTasks.remove(worldName);
        if (batchTask != null) batchTask.cancel();
        mineCenterCache.remove(worldName);
        refreshCountdowns.remove(worldName);
        refreshIntervals.remove(worldName);
    }

    public void shutdown() {
        refreshTasks.values().forEach(BukkitTask::cancel);
        refreshTasks.clear();
        refreshBatchTasks.values().forEach(BukkitTask::cancel);
        refreshBatchTasks.clear();
        mineCenterCache.clear();
        refreshCountdowns.clear();
        refreshIntervals.clear();
    }
}
