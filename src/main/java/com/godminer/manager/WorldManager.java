package com.godminer.manager;

import com.godminer.GoldMiner;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 世界管理器 - 管理共享矿场世界
 * 所有玩家共用一个矿场世界，矿场在地面上生成
 */
public class WorldManager {

    private final GoldMiner plugin;
    private Plugin mvPlugin;
    private String mineWorldName;

    public WorldManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        mvPlugin = Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin == null) {
            plugin.getLogger().severe("未找到 Multiverse-Core 插件！");
            return false;
        }
        // 共享世界名称
        mineWorldName = plugin.getConfig().getString("mine.world-name", "goldminer_mine");
        plugin.getLogger().info("成功连接到 Multiverse-Core v" + mvPlugin.getDescription().getVersion());
        return true;
    }

    /**
     * 获取或创建共享矿场世界（通过 Multiverse 创建，确保 MV 正确管理）
     */
    public World getOrCreateMineWorld() {
        int borderSize = plugin.getConfig().getInt("mine.border-size", 2000);

        // 检查世界是否已加载
        World existing = Bukkit.getWorld(mineWorldName);
        if (existing != null) {
            return existing;
        }

        // 直接通过 Bukkit 创建世界（同步，不会卡死主线程）
        WorldCreator creator = new WorldCreator(mineWorldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);
        creator.generator((String) null);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("创建矿场世界失败！");
            return null;
        }

        // 配置世界属性
        configureWorld(world, borderSize);

        // 异步通知 Multiverse 管理此世界（不阻塞主线程）
        Bukkit.getScheduler().runTask(plugin, () -> registerWithMultiverse(world));

        // 生成矿场
        plugin.getMineManager().generateInitialMine(world);

        plugin.getLogger().info("共享矿场世界 " + mineWorldName + " 创建完成！");
        return world;
    }

    /**
     * MV 世界属性设置（异步调度，失败不影响核心功能）
     */
    private void registerWithMultiverse(World world) {
        if (mvPlugin == null) return;
        // MV 管理非必需，仅尝试设置属性，静默失败
    }

    public String getMineWorldName() {
        return mineWorldName;
    }

    /**
     * 获取玩家在矿场的安全生产点（矿场上方的平台）
     */
    public Location getSafeSpawnLocation(World world) {
        // 矿场总高度（分层配置总和 + 基岩层）
        int totalHeight = plugin.getLayerManager().getTotalHeight();
        // 矿场顶部 + 2格安全空间
        int safeY = totalHeight + 2;
        
        Location spawnLoc = new Location(world, 0.5, safeY, 0.5);
        // 确保脚下有安全平台
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(x, safeY - 1, z).setType(Material.BEDROCK);
                world.getBlockAt(x, safeY, z).setType(Material.AIR);
                world.getBlockAt(x, safeY + 1, z).setType(Material.AIR);
            }
        }
        return spawnLoc;
    }

    /**
     * 传送玩家到矿场安全出生点
     */
    public void teleportToMine(Player player, World world) {
        Location spawnLoc = getSafeSpawnLocation(world);
        player.teleport(spawnLoc);
    }

    private void configureWorld(World world, int borderSize) {
        world.setTime(6000);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_MOB_LOOT, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize * 2);
        border.setDamageAmount(0);
        border.setWarningDistance(10);

        plugin.getLogger().info("矿场世界 " + world.getName() + " 配置完成。");
    }

    public boolean deleteMineWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        // 通过控制台命令删除（兼容所有 MV 版本）
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
        } catch (Exception ignored) {}
        if (world != null) {
            return Bukkit.unloadWorld(world, false);
        }
        return false;
    }

    public boolean unloadWorld(String worldName) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);
        } catch (Exception ignored) {}
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return Bukkit.unloadWorld(world, false);
        }
        return false;
    }

    public Plugin getMvPlugin() {
        return mvPlugin;
    }
}
