package com.godminer;

import com.godminer.command.GoldMinerCommand;
import com.godminer.data.DatabaseManager;
import com.godminer.listener.BlockListener;
import com.godminer.listener.PlayerListener;
import com.godminer.listener.SpecialItemListener;
import com.godminer.manager.*;
import com.godminer.placeholder.GoldMinerExpansion;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * GoldMiner 主插件类
 * 挖矿小游戏插件 for Purpur 1.21.11
 */
public class GoldMiner extends JavaPlugin {

    // 管理器实例
    private DatabaseManager databaseManager;
    private MineralManager mineralManager;
    private WorldManager worldManager;
    private MineManager mineManager;
    private PickaxeManager pickaxeManager;
    private EconomyManager economyManager;
    private TeamManager teamManager;
    private GUIManager guiManager;
    private LeaderboardManager leaderboardManager;
    private PlayerDataManager playerDataManager;
    private ChatInputManager chatInputManager;
    private BossBarManager bossBarManager;
    private ShopManager shopManager;
    private LayerManager layerManager;
    private SpecialItemManager specialItemManager;
    private GoldMinerExpansion papiExpansion;
    private PlayerListener playerListener;

    // 配置文件
    private FileConfiguration langConfig;
    private FileConfiguration layersConfig;
    private FileConfiguration lootConfig;

    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfigs();

        // 初始化管理器
        initManagers();

        // 注册命令
        registerCommands();

        // 注册事件监听器
        registerListeners();

        // 注册 PlaceholderAPI 扩展
        registerPlaceholderExpansion();

        getLogger().info("GoldMiner 插件已成功启用！");
        getLogger().info("作者: 未定awa | 版本: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // 取消注册 PlaceholderAPI 扩展
        if (papiExpansion != null) {
            papiExpansion.unregister();
        }

        // 保存所有玩家数据
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }

        // 清理BossBar
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }

        // 停止所有矿场刷新任务
        if (mineManager != null) {
            mineManager.shutdown();
        }

        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("GoldMiner 插件已安全关闭。");
    }

    private void saveDefaultConfigs() {
        // 保存 config.yml
        saveResourceIfNotExists("config.yml");
        // 保存 lang.yml
        saveResourceIfNotExists("lang.yml");
        // 保存 layers.yml (分层矿场配置)
        saveResourceIfNotExists("layers.yml");
        // 保存 loot.yml (经验瓶/等级升级球/宝箱配置)
        saveResourceIfNotExists("loot.yml");
        // 保存 shop.yml
        saveResourceIfNotExists("shop.yml");

        // 加载 config.yml
        reloadConfig();

        // 加载 lang.yml
        File langFile = new File(getDataFolder(), "lang.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        // 合并默认值
        InputStream langDefault = getResource("lang.yml");
        if (langDefault != null) {
            YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(langDefault, StandardCharsets.UTF_8));
            langConfig.setDefaults(defaultLang);
        }

        // 加载 layers.yml
        File layersFile = new File(getDataFolder(), "layers.yml");
        layersConfig = YamlConfiguration.loadConfiguration(layersFile);
        InputStream layersDefault = getResource("layers.yml");
        if (layersDefault != null) {
            YamlConfiguration defaultLayers = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(layersDefault, StandardCharsets.UTF_8));
            layersConfig.setDefaults(defaultLayers);
        }

        // 加载 loot.yml
        File lootFile = new File(getDataFolder(), "loot.yml");
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        InputStream lootDefault = getResource("loot.yml");
        if (lootDefault != null) {
            YamlConfiguration defaultLoot = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(lootDefault, StandardCharsets.UTF_8));
            lootConfig.setDefaults(defaultLoot);
        }
    }

    private void saveResourceIfNotExists(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }

    private void initManagers() {
        // 数据库管理器
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("数据库初始化失败，插件将无法正常工作！");
        }

        // 分层管理器（加载 layers.yml）
        layerManager = new LayerManager(this);
        layerManager.loadLayers();

        // 矿物管理器（根据层级定义收集奖励）
        mineralManager = new MineralManager(this);
        mineralManager.loadMinerals();

        // 特殊物品管理器（经验瓶/等级升级球/宝箱）
        specialItemManager = new SpecialItemManager(this);
        specialItemManager.loadConfig();

        // 玩家数据管理器
        playerDataManager = new PlayerDataManager(this);
        playerDataManager.loadAll();

        // 经济管理器
        economyManager = new EconomyManager(this);
        economyManager.init();

        // 世界管理器
        worldManager = new WorldManager(this);
        if (!worldManager.init()) {
            getLogger().severe("Multiverse-Core 未找到，世界管理功能将不可用！");
        }

        // 矿场管理器
        mineManager = new MineManager(this);

        // 镐子管理器
        pickaxeManager = new PickaxeManager(this);

        // 小队管理器
        teamManager = new TeamManager(this);

        // 排行榜管理器
        leaderboardManager = new LeaderboardManager(this);

        // BossBar管理器
        bossBarManager = new BossBarManager(this);

        // GUI管理器
        guiManager = new GUIManager(this);

        // 商店管理器
        shopManager = new ShopManager(this);
        shopManager.init();

        // 聊天输入管理器
        chatInputManager = new ChatInputManager(this);
    }

    private void registerCommands() {
        GoldMinerCommand command = new GoldMinerCommand(this);
        var pluginCommand = getCommand("goldminer");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(new SpecialItemListener(this), this);
        // GUIManager 和 ChatInputManager 在构造函数中已注册
    }

    /**
     * 注册 PlaceholderAPI 扩展
     */
    private void registerPlaceholderExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiExpansion = new GoldMinerExpansion(this);
            if (papiExpansion.register()) {
                getLogger().info("PlaceholderAPI 扩展注册成功！");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败！");
            }
        } else {
            getLogger().info("未检测到 PlaceholderAPI，跳过占位符注册。");
        }
    }

    /**
     * 重新加载所有配置
     */
    public void reloadConfigs() {
        reloadConfig();

        // 重新加载 lang.yml
        File langFile = new File(getDataFolder(), "lang.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 重新加载 layers.yml
        File layersFile = new File(getDataFolder(), "layers.yml");
        layersConfig = YamlConfiguration.loadConfiguration(layersFile);

        // 重新加载 loot.yml
        File lootFile = new File(getDataFolder(), "loot.yml");
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);

        // 重新加载 shop.yml
        shopManager.reloadShopConfig();

        // 重新加载层级与矿物奖励
        layerManager.loadLayers();
        mineralManager.loadMinerals();

        // 重新加载特殊物品配置
        specialItemManager.loadConfig();

        // 重启矿场监控任务以应用新参数
        String worldName = getConfig().getString("mine.world-name", "goldminer_mine");
        var world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) {
            mineManager.startRefreshTask(world);
            getLogger().info("矿场监控任务已重启，新参数已生效。");
        }
    }

    // ===== Getter 方法 =====

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MineralManager getMineralManager() {
        return mineralManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public PickaxeManager getPickaxeManager() {
        return pickaxeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public LayerManager getLayerManager() {
        return layerManager;
    }

    public SpecialItemManager getSpecialItemManager() {
        return specialItemManager;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public FileConfiguration getLangConfig() {
        return langConfig;
    }

    public FileConfiguration getLayersConfig() {
        return layersConfig;
    }

    public FileConfiguration getLootConfig() {
        return lootConfig;
    }
}
