package com.godminer.manager;

import com.godminer.GoldMiner;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 矿场管理器 - 负责分层矿场的生成、单层刷新、矿洞与宝箱生成
 * 矿场从上到下分为: 石头区/方解石区/花岗岩区/深板岩区/下界岩区/玄武岩区/黑石区/末地石区，
 * 最底部为 1 格基岩。层级定义见 layers.yml。
 * 不再定时刷新：定期扫描矿场空气占比，达到阈值（默认95%）时自动完整刷新。
 */
public class MineManager {

    private final GoldMiner plugin;
    private final Map<String, BukkitTask> monitorTasks;      // 空气占比监控任务
    private final Map<String, BukkitTask> batchTasks;        // 分批刷新任务
    private final Map<String, BukkitTask> scanTasks;         // 分批扫描任务
    private final Map<String, Location> mineCenterCache;
    private final Map<String, Double> lastAirRatios;         // 世界名 -> 上次扫描空气占比
    private final Map<String, Integer> checkIntervals;       // 世界名 -> 扫描间隔(秒)
    private final NamespacedKey generationVersionKey;        // 矿场生成代际版本标记

    public MineManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.monitorTasks = new HashMap<>();
        this.batchTasks = new HashMap<>();
        this.scanTasks = new HashMap<>();
        this.mineCenterCache = new HashMap<>();
        this.lastAirRatios = new HashMap<>();
        this.checkIntervals = new HashMap<>();
        this.generationVersionKey = new NamespacedKey(plugin, "mine_generation_version");
    }

    // ==================== 基本参数 ====================

    private int getHalfSize() {
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        return Math.max(1, centerSize / 2);
    }

    private int getTotalHeight() {
        return plugin.getLayerManager().getTotalHeight();
    }

    /**
     * 根据高度抽取方块（基岩层/各层级）
     */
    private Material rollBlockAt(int y) {
        return plugin.getLayerManager().rollBlockAt(y, ThreadLocalRandom.current());
    }

    // ==================== 初始生成 ====================

    /**
     * 生成初始矿场（全量：分层方块 + 矿洞 + 宝箱 + 顶部安全平台）
     */
    public void generateInitialMine(World world) {
        refreshMine(world);
    }

    // ==================== 空气占比监控（替代定时刷新） ====================

    /**
     * 启动矿场监控：每 check-interval 秒扫描一次空气占比，
     * 空气占整体区域 ≥ air-threshold 时自动完整刷新矿场。
     */
    public void startRefreshTask(World world) {
        String worldName = world.getName();
        stopRefreshTask(worldName);

        // 检测矿场生成版本：旧版本矿场自动重建一次（保证升级后新机制立即生效）
        ensureMineUpToDate(world);

        int checkInterval = Math.max(5, plugin.getConfig().getInt("mine.check-interval", 10));
        checkIntervals.put(worldName, checkInterval);

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (tick % checkInterval != 0) return;
                // 无人在矿场世界时跳过
                if (world.getPlayers().isEmpty()) return;
                // 已有扫描或刷新在进行中
                if (scanTasks.containsKey(worldName) || batchTasks.containsKey(worldName)) return;
                startAirScan(world);
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒执行一次 tick

        monitorTasks.put(worldName, task);
        plugin.getLogger().info("矿场监控已启动，每 " + checkInterval + " 秒检测一次空气占比。");
    }

    /**
     * 开始分批扫描空气占比（每 tick 处理一批，避免卡顿）
     */
    private void startAirScan(World world) {
        String worldName = world.getName();
        int half = getHalfSize();
        int xSize = half * 2 + 1;
        int zSize = half * 2 + 1;
        int height = getTotalHeight();
        int total = xSize * zSize * height;
        int batchSize = getBatchSize();

        int[] cursor = {0};
        int[] airCount = {0};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (cursor[0] < total && processed < batchSize) {
                    int idx = cursor[0]++;
                    int y = idx / (xSize * zSize);
                    int rem = idx % (xSize * zSize);
                    int zOff = rem / xSize;
                    int xOff = rem % xSize;
                    Material mat = world.getBlockAt(-half + xOff, y, -half + zOff).getType();
                    if (mat == Material.AIR || mat == Material.CAVE_AIR) {
                        airCount[0]++;
                    }
                    processed++;
                }

                if (cursor[0] >= total) {
                    scanTasks.remove(worldName);
                    double airRatio = total > 0 ? (double) airCount[0] / total : 0.0;
                    lastAirRatios.put(worldName, airRatio);

                    double threshold = Math.min(100.0, Math.max(0.0,
                            plugin.getConfig().getDouble("mine.air-threshold", 95.0)));
                    if (airRatio * 100.0 >= threshold) {
                        plugin.getLogger().info("矿场 " + worldName + " 空气占比 "
                                + String.format("%.2f", airRatio * 100.0)
                                + "% ≥ " + threshold + "%，开始自动刷新。");
                        triggerAutoRefresh(world, airRatio);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        scanTasks.put(worldName, task);
    }

    private int getBatchSize() {
        return Math.max(1000, plugin.getConfig().getInt("mine.refresh-batch-size", 20000));
    }

    /**
     * 检查矿场生成代际版本：
     * 世界持久化数据中记录的版本与 config.yml 的 mine.generation-version 不一致时，
     * 自动完整重建矿场（升级插件/调整生成机制后无需手动删除世界）。
     */
    private void ensureMineUpToDate(World world) {
        int required = plugin.getConfig().getInt("mine.generation-version", 1);
        Integer stored = world.getPersistentDataContainer().get(generationVersionKey, PersistentDataType.INTEGER);
        if (stored == null || stored != required) {
            plugin.getLogger().info("矿场世界 " + world.getName() + " 生成版本已过时（当前 "
                    + (stored == null ? "无" : stored) + "，需要 " + required + "），正在自动重建...");
            refreshMine(world);
        }
    }

    /**
     * 获取刷新倒计时（秒） - 返回当前扫描间隔，兼容旧占位符
     */
    public int getRefreshCountdown(String worldName) {
        return checkIntervals.getOrDefault(worldName, 10);
    }

    /**
     * 获取刷新进度 (0.0 ~ 1.0) - 返回矿场剩余方块占比（1 - 空气占比）
     */
    public float getRefreshProgress(String worldName) {
        double airRatio = lastAirRatios.getOrDefault(worldName, 0.0);
        return (float) Math.max(0f, Math.min(1f, 1.0 - airRatio));
    }

    /**
     * 判断玩家位置是否安全（头脚两格必须有空气）
     */
    private boolean isPlayerSafe(Player player) {
        Location loc = player.getLocation();
        int halfSize = getHalfSize();
        int totalHeight = getTotalHeight();

        // 玩家在矿场范围外，安全
        if (Math.abs(loc.getBlockX()) > halfSize + 5 || Math.abs(loc.getBlockZ()) > halfSize + 5) {
            return true;
        }

        // 玩家在矿场高度范围外，安全
        if (loc.getBlockY() < 0 || loc.getBlockY() > totalHeight + 10) {
            return true;
        }

        Location feetLoc = loc.clone();
        Location headLoc = loc.clone().add(0, 1, 0);

        Material feetBlock = feetLoc.getBlock().getType();
        Material headBlock = headLoc.getBlock().getType();

        if (feetBlock.isSolid() && feetBlock != Material.GLASS) {
            return false;
        }
        return !headBlock.isSolid() || headBlock == Material.GLASS;
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
        BukkitTask task = monitorTasks.remove(worldName);
        if (task != null) {
            task.cancel();
        }
    }

    // ==================== 矿场刷新 ====================

    /**
     * 完整刷新矿场：分层重新生成所有方块，再生成矿洞与宝箱
     */
    public void refreshMine(World world) {
        regenerate(world, null, true);
    }

    /**
     * 只刷新指定层级（bedrock 表示基岩层）
     */
    public void refreshLayer(World world, String layerKey) {
        regenerate(world, layerKey, true);
    }

    /**
     * 空气占比达标触发的自动刷新：先提示玩家，再完整刷新
     */
    private void triggerAutoRefresh(World world, double airRatio) {
        broadcast(world, "mine.auto-refresh-start",
                "{percent}", String.format("%.1f", airRatio * 100.0));
        regenerate(world, null, false);
    }

    /**
     * 分批重新生成矿场方块（layerKey 为 null 时生成全部层级并在完成后生成矿洞）
     * 每 tick 处理一批，避免主线程一次性处理百万方块造成卡顿。
     * @param notifyStart 是否向矿场内玩家广播刷新开始提示
     */
    private void regenerate(World world, String layerKey, boolean notifyStart) {
        String worldName = world.getName();

        // 取消正在进行的批次与扫描
        cancelBatchTask(worldName);
        BukkitTask scanTask = scanTasks.remove(worldName);
        if (scanTask != null) scanTask.cancel();

        int half = getHalfSize();
        int xSize = half * 2 + 1;
        int zSize = half * 2 + 1;
        int totalHeight = getTotalHeight();

        int yStart = 0;
        int yEnd = totalHeight;
        if (layerKey != null) {
            int[] range = plugin.getLayerManager().getYRange(layerKey);
            if (range == null) {
                plugin.getLogger().warning("不存在的层级: " + layerKey + "，刷新已取消。");
                return;
            }
            yStart = range[0];
            yEnd = range[1];
        }

        int yCount = yEnd - yStart;
        int total = xSize * yCount * zSize;
        int batchSize = getBatchSize();

        final int fHalf = half, fXSize = xSize, fZSize = zSize, fTotal = total, fYStart = yStart;
        final String fLayer = layerKey;

        BukkitTask task = new BukkitRunnable() {
            int cursor = 0;

            @Override
            public void run() {
                int processed = 0;
                while (cursor < fTotal && processed < batchSize) {
                    int idx = cursor++;
                    int y = fYStart + idx / (fXSize * fZSize);
                    int rem = idx % (fXSize * fZSize);
                    int zOff = rem / fXSize;
                    int xOff = rem % fXSize;
                    world.getBlockAt(-fHalf + xOff, y, -fHalf + zOff).setType(rollBlockAt(y), false);
                    processed++;
                }

                if (cursor >= fTotal) {
                    batchTasks.remove(worldName);
                    if (fLayer == null) {
                        carveCaves(world);
                        placeSafetyPlatform(world);
                        // 记录本次生成的代际版本，避免下次进入时重复重建
                        world.getPersistentDataContainer().set(generationVersionKey, PersistentDataType.INTEGER,
                                plugin.getConfig().getInt("mine.generation-version", 1));
                        plugin.getLogger().info("矿场 " + worldName + " 已完整刷新（含矿洞与宝箱），共 " + fTotal + " 个方块。");
                        broadcast(world, "mine.refresh-complete");
                    } else {
                        plugin.getLogger().info("矿场 " + worldName + " 层级 " + fLayer + " 已刷新，共 " + fTotal + " 个方块。");
                    }
                    checkPlayerSafety(world);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        batchTasks.put(worldName, task);
        if (notifyStart) {
            if (fLayer == null) {
                broadcast(world, "mine.refresh-start");
            } else {
                broadcast(world, "mine.layer-refresh-start",
                        "{layer}", plugin.getLayerManager().getDisplayName(fLayer));
            }
        }
        if (fLayer == null) {
            plugin.getLogger().info("矿场 " + worldName + " 开始完整刷新，共 " + fTotal + " 方块，每tick " + batchSize + " 块。");
        } else {
            plugin.getLogger().info("矿场 " + worldName + " 开始刷新层级 " + fLayer + "，共 " + fTotal + " 方块。");
        }
    }

    /**
     * 向矿场世界内的所有玩家广播一条可配置消息
     * @param key          lang.yml 中的消息键
     * @param replacements {占位符, 值} 成对出现
     */
    private void broadcast(World world, String key, String... replacements) {
        String msg = plugin.getLangConfig().getString(key, null);
        if (msg == null || msg.isEmpty()) return;
        msg = com.godminer.util.MessageUtil.replacePlaceholders(msg, replacements);
        for (Player player : world.getPlayers()) {
            com.godminer.util.MessageUtil.sendMessage(player, msg);
        }
    }

    private void cancelBatchTask(String worldName) {
        BukkitTask old = batchTasks.remove(worldName);
        if (old != null) old.cancel();
    }

    // ==================== 矿洞与宝箱 ====================

    /**
     * 在矿场内部生成随机矿洞（随机游走挖空），部分矿洞放置宝箱
     */
    private void carveCaves(World world) {
        ConfigurationSection caveSec = plugin.getLayersConfig().getConfigurationSection("caves");
        if (caveSec == null || !caveSec.getBoolean("enabled", true)) return;

        int half = getHalfSize();
        int totalHeight = getTotalHeight();
        int bedrockHeight = plugin.getLayerManager().getBedrockHeight();

        double volume = (double) (half * 2 + 1) * (half * 2 + 1) * totalHeight;
        double perTenK = caveSec.getDouble("count-per-10000", 2.0);
        int count = Math.max(0, (int) Math.round(volume / 10000.0 * perTenK));
        if (count == 0) return;

        int minLen = Math.max(5, caveSec.getInt("min-length", 30));
        int maxLen = Math.max(minLen, caveSec.getInt("max-length", 70));
        int minR = Math.max(1, caveSec.getInt("min-radius", 1));
        int maxR = Math.max(minR, caveSec.getInt("max-radius", 2));
        double chestChance = Math.min(1.0, Math.max(0.0, caveSec.getDouble("chest-chance", 0.3)));

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 第一步：挖出所有矿洞并记录路径
        List<List<int[]>> cavePaths = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = rnd.nextInt(-half + 3, half - 2);
            int z = rnd.nextInt(-half + 3, half - 2);
            int y = rnd.nextInt(bedrockHeight + 2, totalHeight - 2);
            int steps = rnd.nextInt(minLen, maxLen + 1);

            List<int[]> path = new ArrayList<>(steps);
            for (int s = 0; s < steps; s++) {
                carveSphere(world, x, y, z, rnd.nextInt(minR, maxR + 1), half, totalHeight, bedrockHeight);
                path.add(new int[]{x, y, z});

                // 随机游走（偏向水平方向）
                int dir = rnd.nextInt(8);
                switch (dir) {
                    case 0 -> x++;
                    case 1 -> x--;
                    case 2 -> z++;
                    case 3 -> z--;
                    case 4 -> y++;
                    case 5 -> y--;
                    default -> {
                        if (rnd.nextBoolean()) {
                            x += rnd.nextBoolean() ? 1 : -1;
                        } else {
                            z += rnd.nextBoolean() ? 1 : -1;
                        }
                    }
                }
                x = Math.max(-half + 1, Math.min(half - 1, x));
                z = Math.max(-half + 1, Math.min(half - 1, z));
                y = Math.max(bedrockHeight + 1, Math.min(totalHeight - 1, y));
            }
            cavePaths.add(path);
        }

        // 第二步：所有矿洞挖完后统一放置宝箱，避免宝箱被后续矿洞挖掉
        int chestsPlaced = 0;
        for (List<int[]> path : cavePaths) {
            if (rnd.nextDouble() < chestChance && tryPlaceChest(world, path, bedrockHeight)) {
                chestsPlaced++;
            }
        }

        plugin.getLogger().info("矿场矿洞已生成: " + count + " 个，其中宝箱 " + chestsPlaced + " 个。");
    }

    /**
     * 以 (cx,cy,cz) 为球心挖空半径 r 的球体
     */
    private void carveSphere(World world, int cx, int cy, int cz, int r,
                             int half, int totalHeight, int bedrockHeight) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (Math.abs(x) > half || Math.abs(z) > half) continue;
                    if (y < bedrockHeight || y >= totalHeight) continue;
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * 在矿洞路径上寻找合适的位置放置宝箱：
     * 沿路径点（及其水平邻居）向下找到矿洞地面（第一个非空气方块），把宝箱放在地面上方，
     * 并要求宝箱上方仍有空间（避免被顶部封死打不开）。
     */
    private boolean tryPlaceChest(World world, List<int[]> path, int bedrockHeight) {
        if (path.isEmpty()) return false;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int totalHeight = getTotalHeight();
        int half = getHalfSize();

        // 打乱路径点顺序，提高覆盖度
        List<int[]> shuffled = new ArrayList<>(path);
        Collections.shuffle(shuffled, rnd);

        for (int[] p : shuffled) {
            // 在路径点周围的 3x3 水平区域内寻找可放置宝箱的地面
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = p[0] + dx;
                    int z = p[2] + dz;
                    if (Math.abs(x) > half || Math.abs(z) > half) continue;

                    // 从该位置向下找矿洞地面
                    int y = p[1];
                    while (y > bedrockHeight && isAir(world.getBlockAt(x, y, z).getType())) {
                        y--;
                    }
                    int chestY = y + 1;
                    if (chestY >= totalHeight || chestY + 1 >= totalHeight) continue;

                    // 地面必须存在（y 处为固体方块）
                    Block ground = world.getBlockAt(x, y, z);
                    if (isAir(ground.getType())) continue;

                    // 宝箱位置必须是空气，且上方有空间
                    Block at = world.getBlockAt(x, chestY, z);
                    if (!isAir(at.getType())) continue;
                    Block above = world.getBlockAt(x, chestY + 1, z);
                    if (above.getType().isSolid()) continue;

                    at.setType(Material.CHEST, false);
                    if (at.getState() instanceof Chest chest) {
                        plugin.getSpecialItemManager().fillChest(chest);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断方块是否为空气（含洞穴空气）
     */
    private boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR;
    }

    /**
     * 在矿场顶部放置安全平台（基岩 + 上方空气）
     */
    private void placeSafetyPlatform(World world) {
        int platformY = getTotalHeight();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, platformY, z).setType(Material.BEDROCK, false);
                world.getBlockAt(x, platformY + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, platformY + 2, z).setType(Material.AIR, false);
            }
        }
        mineCenterCache.put(world.getName(), new Location(world, 0.5, platformY + 1, 0.5));
    }

    // ==================== 清理 ====================

    public void cleanupWorld(String worldName) {
        stopRefreshTask(worldName);
        cancelBatchTask(worldName);
        BukkitTask scanTask = scanTasks.remove(worldName);
        if (scanTask != null) scanTask.cancel();
        mineCenterCache.remove(worldName);
        lastAirRatios.remove(worldName);
        checkIntervals.remove(worldName);
    }

    public void shutdown() {
        monitorTasks.values().forEach(BukkitTask::cancel);
        monitorTasks.clear();
        batchTasks.values().forEach(BukkitTask::cancel);
        batchTasks.clear();
        scanTasks.values().forEach(BukkitTask::cancel);
        scanTasks.clear();
        mineCenterCache.clear();
        lastAirRatios.clear();
        checkIntervals.clear();
    }
}
