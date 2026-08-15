package com.godminer.command;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.model.Team;
import com.godminer.manager.ShopManager;
import com.godminer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 主命令处理器
 */
public class GoldMinerCommand implements CommandExecutor, TabCompleter {

    private final GoldMiner plugin;

    public GoldMinerCommand(GoldMiner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join":
                return handleJoin(sender);
            case "shop":
                return handleShop(sender, args);
            case "info":
                return handleInfo(sender);
            case "buy":
                return handleBuy(sender, args);
            case "team":
                return handleTeam(sender, args);
            case "top":
                return handleTop(sender);
            case "exchange":
                return handleExchange(sender, args);
            case "suit":
                return handleSuit(sender);
            case "set":
                return handleSet(sender, args);
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "reload":
                return handleReload(sender, args);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&6===== GoldMiner 帮助 =====");
        MessageUtil.sendMessage(sender, "&e/goldminer join &7- 加入矿场");
        MessageUtil.sendMessage(sender, "&e/goldminer shop &7- 打开矿场商店");
        MessageUtil.sendMessage(sender, "&e/goldminer info &7- 查看矿工信息");
        MessageUtil.sendMessage(sender, "&e/goldminer suit &7- 切换装备显示");
        MessageUtil.sendMessage(sender, "&e/goldminer team create &7- 创建小队");
        MessageUtil.sendMessage(sender, "&e/goldminer team join [队名] &7- 加入小队");
        MessageUtil.sendMessage(sender, "&e/goldminer team accept [玩家名] &7- 接受入队申请");
        MessageUtil.sendMessage(sender, "&e/goldminer team leave &7- 退出小队");
        MessageUtil.sendMessage(sender, "&e/goldminer team list &7- 查看小队列表");
        MessageUtil.sendMessage(sender, "&e/goldminer top &7- 查看排行榜");
        MessageUtil.sendMessage(sender, "&e/goldminer exchange <数量> &7- 兑换货币");
        MessageUtil.sendMessage(sender, "&e/goldminer buy lv <数量> &7- 精确购买等级");
        if (sender.hasPermission("goldminer.admin")) {
            MessageUtil.sendMessage(sender, "&c/goldminer reload &7- 重载所有配置文件");
            MessageUtil.sendMessage(sender, "&c/goldminer reload pool &7- 强制刷新矿池");
            MessageUtil.sendMessage(sender, "&c/goldminer reload info &7- 刷新玩家与矿场信息");
            MessageUtil.sendMessage(sender, "&c/goldminer shop set <商品key> <价格> &7- 修改商店价格");
            MessageUtil.sendMessage(sender, "&c/goldminer set exp|lv <玩家> <数量> &7- 设置经验/等级");
            MessageUtil.sendMessage(sender, "&c/goldminer add exp|lv <玩家> <数量> &7- 添加经验/等级");
            MessageUtil.sendMessage(sender, "&c/goldminer remove exp|lv <玩家> <数量> &7- 移除经验/等级");
        }
    }

    private boolean handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        if (!player.hasPermission("goldminer.join")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // 检查玩家是否真的在矿场世界中
        String mineWorldName = plugin.getWorldManager().getMineWorldName();
        if (data.getCurrentMineWorld() != null) {
            // 如果 tracking 显示在矿场，但玩家实际不在 → 清除记录，允许重新加入
            if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) {
                data.setCurrentMineWorld(null);
            } else {
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("join.already-in-mine", "&c你已经在矿场中了！"));
                return true;
            }
        }

        // 获取或创建共享矿场世界
        MessageUtil.sendMessage(player, plugin.getLangConfig().getString("join.creating-world", "&a正在准备矿场世界..."));
        World world = plugin.getWorldManager().getOrCreateMineWorld();
        if (world == null) {
            MessageUtil.sendMessage(player, "&c准备矿场世界失败！请联系管理员。");
            return true;
        }

        // 设置玩家数据（首次加入才初始化镐子数据）
        boolean isFirstJoin = data.getCurrentMineWorld() == null && data.getPickaxeTier() == com.godminer.model.PickaxeTier.WOOD
                && data.getLevel() == 1;
        data.setCurrentMineWorld(world.getName());
        if (isFirstJoin) {
            data.setPickaxeTier(com.godminer.model.PickaxeTier.WOOD);
            data.setEfficiencyLevel(1);
            data.setUnbreakingLevel(1);
        }

        // 传送玩家到安全出生点
        plugin.getWorldManager().teleportToMine(player, world);
        MessageUtil.sendMessage(player, plugin.getLangConfig().getString("join.teleported", "&a欢迎来到矿场！"));

        // 给予初始物品
        player.getInventory().setItem(0, plugin.getPickaxeManager().createInitialPickaxe());
        player.getInventory().setItem(1, plugin.getGUIManager().createMenuStar());
        player.getInventory().setItem(2, plugin.getPickaxeManager().createGlass());

        // 应用药水效果
        plugin.getPlayerListener().ensureMineItems(player, data);

        // 启动刷新任务（如果还没启动）
        plugin.getMineManager().startRefreshTask(world);

        // 更新BossBar
        plugin.getBossBarManager().updateBossBar(player);

        return true;
    }

    private boolean handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&e用法: &a/goldminer team <create|join|accept|leave|list>");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create":
                return handleTeamCreate(player, args);
            case "join":
                return handleTeamJoin(player, args);
            case "accept":
                return handleTeamAccept(player, args);
            case "leave":
                return handleTeamLeave(player);
            case "list":
                return handleTeamList(player);
            default:
                MessageUtil.sendMessage(player, "&e用法: &a/goldminer team <create|join|accept|leave|list>");
                return true;
        }
    }

    private boolean handleTeamCreate(Player player, String[] args) {
        if (!player.hasPermission("goldminer.team.create")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        // 检查是否已有队伍
        if (plugin.getTeamManager().getTeamByPlayer(uuid) != null) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.already-in-team", "&c你已经在一个小队中！"));
            return true;
        }

        // 如果提供了队伍名称参数
        if (args.length >= 3) {
            String teamName = args[2];
            Team team = plugin.getTeamManager().createTeam(teamName, uuid);
            if (team == null) {
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.name-taken", "&c该小队名称已被使用！"));
                return true;
            }
            String msg = plugin.getLangConfig().getString("team.create-success", "&a小队 &e{team} &a创建成功！");
            msg = MessageUtil.replacePlaceholders(msg, "{team}", teamName);
            MessageUtil.sendMessage(player, msg);
        } else {
            // 提示输入队伍名称
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.create-prompt",
                    "&a请在聊天栏输入小队名称，输入 &cC &a取消。"));
            // 等待聊天输入的处理在监听器中
            plugin.getChatInputManager().expectInput(player, "team_create");
        }

        return true;
    }

    private boolean handleTeamJoin(Player player, String[] args) {
        if (!player.hasPermission("goldminer.team.join")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.join-usage",
                    "&e使用 &a/goldminer team join <队名> &e来加入小队。"));
            return true;
        }

        String teamName = args[2];
        boolean applied = plugin.getTeamManager().applyToTeam(player.getUniqueId(), teamName);
        if (!applied) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.no-team", "&c该小队不存在或无法加入！"));
            return true;
        }

        MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.application-submitted", "&a你的入队申请已提交！"));
        return true;
    }

    private boolean handleTeamAccept(Player player, String[] args) {
        if (!player.hasPermission("goldminer.team.accept")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        UUID leaderUuid = player.getUniqueId();

        if (args.length >= 3) {
            // 接受指定玩家的申请
            String targetName = args[2];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                MessageUtil.sendMessage(player, "&c玩家 " + targetName + " 不在线或不存在！");
                return true;
            }

            boolean accepted = plugin.getTeamManager().acceptApplication(leaderUuid, target.getUniqueId());
            if (accepted) {
                String msg = plugin.getLangConfig().getString("team.accept-success", "&a玩家 &e{player} &a已加入你的小队！");
                msg = MessageUtil.replacePlaceholders(msg, "{player}", targetName);
                MessageUtil.sendMessage(player, msg);
                MessageUtil.sendMessage(target, "&a你已加入 &e" +
                        plugin.getTeamManager().getTeamByPlayer(leaderUuid).getName() + " &a小队！");
            } else {
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.application-no-pending",
                        "&c没有该玩家的待处理申请！"));
            }
        } else {
            // 接受所有申请
            int count = plugin.getTeamManager().acceptAllApplications(leaderUuid);
            if (count > 0) {
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.accept-all-success",
                        "&a所有待处理的入队申请已通过！（共 " + count + " 人）"));
            } else {
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.application-no-pending",
                        "&c没有待处理的入队申请！"));
            }
        }

        return true;
    }

    private boolean handleTeamLeave(Player player) {
        if (!player.hasPermission("goldminer.team.leave")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        boolean left = plugin.getTeamManager().leaveTeam(player.getUniqueId());
        if (left) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.leave-success",
                    "&a你已退出小队，经验和等级已清空。"));
        } else {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.not-in-team", "&c你不在任何小队中！"));
        }

        return true;
    }

    private boolean handleTeamList(Player player) {
        if (!player.hasPermission("goldminer.team.list")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        Collection<Team> teams = plugin.getTeamManager().getAllTeams();
        String header = plugin.getLangConfig().getString("team.list-header", "&6===== 小队列表 =====");
        MessageUtil.sendMessage(player, header);

        if (teams.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("team.list-empty", "&7当前没有小队。"));
            return true;
        }

        int index = 1;
        for (Team team : teams) {
            String leaderName = Bukkit.getOfflinePlayer(team.getLeaderUuid()).getName();
            String entry = plugin.getLangConfig().getString("team.list-entry",
                    "&e{index}. &b{team} &7- 队长: &a{leader} &7- 成员数: &e{size}");
            entry = MessageUtil.replacePlaceholders(entry,
                    "{index}", String.valueOf(index),
                    "{team}", team.getName(),
                    "{leader}", leaderName != null ? leaderName : "Unknown",
                    "{size}", String.valueOf(team.getSize()));
            MessageUtil.sendMessage(player, entry);
            index++;
        }

        return true;
    }

    private boolean handleTop(CommandSender sender) {
        if (!sender.hasPermission("goldminer.top")) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        if (sender instanceof Player player) {
            plugin.getLeaderboardManager().showLeaderboard(player);
        } else {
            // 控制台显示
            Map<UUID, PlayerData> allData = plugin.getPlayerDataManager().getAllPlayerData();
            List<PlayerData> sorted = allData.values().stream()
                    .sorted(Comparator.comparingInt(PlayerData::getCoins).reversed()
                            .thenComparingInt(PlayerData::getLevel).reversed())
                    .limit(5)
                    .toList();

            plugin.getLogger().info("===== 矿场排行榜 =====");
            int rank = 1;
            for (PlayerData data : sorted) {
                plugin.getLogger().info(rank + ". " + data.getPlayerName() +
                        " - 金币: " + data.getCoins() + " - 等级: " + data.getLevel());
                rank++;
            }
        }
        return true;
    }

    private boolean handleExchange(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        if (args.length < 2) {
            // 打开兑换GUI
            plugin.getGUIManager().openExchangeMenu(player);
            return true;
        }

        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                MessageUtil.sendMessage(player, "&c请输入有效的正整数！");
                return true;
            }
            plugin.getEconomyManager().exchangeCoins(player, amount);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "&c请输入有效的数字！");
        }

        return true;
    }

    // ===== 矿场商店 =====

    private boolean handleShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        // /goldminer shop set <key> <price> - 管理员命令
        if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("goldminer.admin")) {
                MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
                return true;
            }
            String key = args[2];
            try {
                int price = Integer.parseInt(args[3]);
                boolean success = plugin.getShopManager().setShopPrice(key, price);
                if (success) {
                    MessageUtil.sendMessage(sender, "&a商店价格已更新: &e" + key + " &a→ &6" + price + " 金币");
                } else {
                    MessageUtil.sendMessage(sender, "&c未找到商品key: " + key);
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, "&c价格必须为整数！");
            } catch (ArrayIndexOutOfBoundsException e) {
                MessageUtil.sendMessage(sender, "&c用法: /goldminer shop set <商品key> <价格>");
            }
            return true;
        }

        // 打开商店GUI
        plugin.getGUIManager().openShopMenu(player);
        return true;
    }

    // ===== 矿工信息 =====

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            MessageUtil.sendMessage(player, "&c无法获取你的矿工数据！");
            return true;
        }

        String teamName = data.getTeamName();
        if (teamName == null) teamName = "无";

        double critRate = data.getCritHitRate() * 100;
        double critMag = data.getEffectiveCritMagnification();
        int critTime = data.getCritMagRemainingSeconds();

        MessageUtil.sendMessage(player, "&6===== &b&l我的矿工信息 &6=====");
        MessageUtil.sendMessage(player, "&e玩家名称: &f" + player.getName());
        MessageUtil.sendMessage(player, "&e所属小队: &f" + teamName);
        MessageUtil.sendMessage(player, "&e金币: &6" + MessageUtil.formatNumber(data.getCoins()));
        MessageUtil.sendMessage(player, "&e经验: &a" + MessageUtil.formatNumber(data.getExp())
                + " &7/ &a" + MessageUtil.formatNumber(data.getExpToNextLevel()));
        MessageUtil.sendMessage(player, "&e等级: &bLv." + data.getLevel());
        MessageUtil.sendMessage(player, "&e镐子: &f" + data.getPickaxeTier().name());
        MessageUtil.sendMessage(player, "&e挖矿暴击率: &c" + String.format("%.1f%%", critRate));
        MessageUtil.sendMessage(player, "&e挖矿暴击倍率: &c" + String.format("%.1f", critMag) + "倍");
        MessageUtil.sendMessage(player, "&e额外倍率剩余: &e" + ShopManager.formatDuration(critTime));
        MessageUtil.sendMessage(player, "&6============================");
        return true;
    }

    // ===== buy lv 精确购买等级 =====

    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        if (args.length < 3 || !args[1].equalsIgnoreCase("lv")) {
            MessageUtil.sendMessage(player, "&e用法: &a/goldminer buy lv <数量> [confirm]");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                MessageUtil.sendMessage(player, "&c请输入有效的正整数！");
                return true;
            }

            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data == null) {
                MessageUtil.sendMessage(player, "&c无法获取你的数据！");
                return true;
            }

            int price = plugin.getShopManager().calculateLevelPriceForAmount(data, amount);
            int targetLevel = data.getLevel() + amount;
            int expNeeded = data.getExpToTargetLevel(targetLevel);

            // 如果带confirm参数则直接执行购买
            if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
                boolean bought = plugin.getShopManager().buyLevels(player, data, amount);
                if (bought) {
                    // 检查镐子升级
                    String upgradeResult = plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
                    if (upgradeResult != null) {
                        var inv = player.getInventory();
                        for (int i = 0; i < inv.getSize(); i++) {
                            var item = inv.getItem(i);
                            if (item != null && item.getItemMeta() != null && item.getItemMeta().isUnbreakable()
                                    && item.getType().toString().endsWith("_PICKAXE")) {
                                inv.setItem(i, null);
                                break;
                            }
                        }
                        inv.setItem(0, plugin.getPickaxeManager().createPickaxe(data));
                        if (data.isSuitVisible()) {
                            plugin.getPickaxeManager().equipArmor(player, data);
                        }
                    }
                    plugin.getBossBarManager().updateBossBar(player);
                }
                return true;
            }

            // 未带confirm → 显示购买预览
            MessageUtil.sendMessage(player, "&6===== 精确购买等级 =====");
            MessageUtil.sendMessage(player, "&e当前等级: &bLv." + data.getLevel());
            MessageUtil.sendMessage(player, "&e目标等级: &bLv." + targetLevel);
            MessageUtil.sendMessage(player, "&e购买数量: &a" + amount + "级");
            MessageUtil.sendMessage(player, "&e所需经验: &a" + MessageUtil.formatNumber(expNeeded));
            MessageUtil.sendMessage(player, "&e所需金币: &6" + MessageUtil.formatNumber(price));
            MessageUtil.sendMessage(player, "&7公式: 所需总经验 × 经验单价系数");
            MessageUtil.sendMessage(player, "&a确认购买请执行: &e/goldminer buy lv " + amount + " confirm");

            return true;
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "&c请输入有效的数字！");
            return true;
        }
    }

    // ===== reload 命令（配置重载 / 矿池刷新 / 信息刷新） =====

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("goldminer.admin")) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }

        // /goldminer reload → 重载配置
        if (args.length < 2) {
            plugin.reloadConfigs();
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.reload", "&a配置已重新加载！"));
            return true;
        }

        String action = args[1].toLowerCase();

        // /goldminer reload pool → 强制刷新矿池
        if (action.equals("pool")) {
            String worldName = plugin.getConfig().getString("mine.world-name", "goldminer_mine");
            World mineWorld = Bukkit.getWorld(worldName);
            if (mineWorld == null) {
                MessageUtil.sendMessage(sender, "&c矿场世界 " + worldName + " 未加载！请先让玩家执行 /goldminer join。");
                return true;
            }
            plugin.getMineManager().refreshMine(mineWorld);
            Location safeLoc = plugin.getMineManager().getSafeLocation(mineWorld);
            for (Player p : mineWorld.getPlayers()) {
                if (!isPlayerSafeInMine(p)) {
                    p.teleport(safeLoc);
                    MessageUtil.sendMessage(p, "&c矿池刷新后检测到你处于危险位置，已传送至安全区域！");
                }
            }
            MessageUtil.sendMessage(sender, "&a矿池已强制刷新！矿物已根据配置重新生成。");
            return true;
        }

        // /goldminer reload info → 刷新玩家与矿场世界信息
        if (action.equals("info")) {
            // 先保存所有玩家当前矿场世界状态（DB不存此字段）
            Map<UUID, String> mineWorlds = new HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
                if (pd != null && pd.getCurrentMineWorld() != null) {
                    mineWorlds.put(p.getUniqueId(), pd.getCurrentMineWorld());
                }
            }

            // 保存并重新加载数据库
            plugin.getPlayerDataManager().saveAll();
            plugin.getPlayerDataManager().loadAll();

            // 恢复在线玩家的矿场世界状态
            for (Player p : Bukkit.getOnlinePlayers()) {
                String world = mineWorlds.get(p.getUniqueId());
                if (world != null) {
                    PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
                    if (pd != null) {
                        pd.setCurrentMineWorld(world);
                    }
                }
            }

            // 重启矿场刷新任务
            String worldName = plugin.getConfig().getString("mine.world-name", "goldminer_mine");
            World mineWorld = Bukkit.getWorld(worldName);
            if (mineWorld != null) {
                plugin.getMineManager().stopRefreshTask(worldName);
                plugin.getMineManager().startRefreshTask(mineWorld);
            }
            MessageUtil.sendMessage(sender, "&a玩家数据与矿场信息已刷新！");
            return true;
        }

        MessageUtil.sendMessage(sender, "&e用法: &a/goldminer reload [pool|info]");
        return true;
    }

    private boolean isPlayerSafeInMine(Player player) {
        var loc = player.getLocation();
        int centerSize = plugin.getConfig().getInt("mine.center-size", 100);
        int halfSize = centerSize / 2;
        if (Math.abs(loc.getBlockX()) > halfSize + 5 || Math.abs(loc.getBlockZ()) > halfSize + 5) return true;
        if (loc.getBlockY() < 0 || loc.getBlockY() > centerSize + 10) return true;
        var feet = loc.clone();
        var head = loc.clone().add(0, 1, 0);
        return !feet.getBlock().getType().isSolid() && !head.getBlock().getType().isSolid();
    }

    private boolean handleSuit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.player-only", "&c该命令只能由玩家执行！"));
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("suit.not-in-mine", "&c你需要在矿场中使用此命令！"));
            return true;
        }

        data.toggleSuit();
        if (data.isSuitVisible()) {
            plugin.getPickaxeManager().equipArmor(player, data);
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("suit.equipped", "&a装备已穿戴！"));
        } else {
            plugin.getPickaxeManager().clearArmor(player);
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString("suit.unequipped", "&c装备已卸下！"));
        }
        return true;
    }

    // ===== Admin 命令 =====

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("goldminer.admin")) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&c用法: /goldminer set exp|lv <玩家> <数量>");
            return true;
        }
        return modifyPlayerStat(sender, args, "set");
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("goldminer.admin")) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&c用法: /goldminer add exp|lv <玩家> <数量>");
            return true;
        }
        return modifyPlayerStat(sender, args, "add");
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("goldminer.admin")) {
            MessageUtil.sendMessage(sender, plugin.getLangConfig().getString("messages.no-permission", "&c你没有权限！"));
            return true;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&c用法: /goldminer remove exp|lv <玩家> <数量>");
            return true;
        }
        return modifyPlayerStat(sender, args, "remove");
    }

    private boolean modifyPlayerStat(CommandSender sender, String[] args, String action) {
        // args: [exp|lv] [player] [amount]  (sub已被消耗)
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&c用法: /goldminer " + action + " exp|lv <玩家> <数量>");
            return true;
        }
        String type = args[1].toLowerCase();
        String targetName = args[2];
        int amountIdx = 3;

        if (args.length < 4 && sender instanceof Player) {
            // 没指定玩家，使用发送者
            targetName = ((Player) sender).getName();
            amountIdx = 2;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(sender, "&c玩家 " + targetName + " 不在线！");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[amountIdx]);
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, "&c请输入有效的数字！");
            return true;
        }

        if (amount < 0) {
            MessageUtil.sendMessage(sender, "&c数量不能为负数！");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            MessageUtil.sendMessage(sender, "&c无法获取玩家数据！");
            return true;
        }

        switch (type) {
            case "exp": {
                int oldExp = data.getExp();
                switch (action) {
                    case "set": data.setExp(amount); break;
                    case "add": data.addExp(amount); break;
                    case "remove": data.setExp(Math.max(0, oldExp - amount)); break;
                }
                processLevelUp(target, data);
                MessageUtil.sendMessage(sender, "&a已将 " + target.getName() + " 的经验从 " + oldExp
                        + " 调整为 " + data.getExp() + "（等级: " + data.getLevel() + "）");
                break;
            }
            case "lv": {
                int oldLevel = data.getLevel();
                int newLevel;
                switch (action) {
                    case "set": newLevel = Math.max(1, amount); break;
                    case "add": newLevel = oldLevel + amount; break;
                    case "remove": newLevel = Math.max(1, oldLevel - amount); break;
                    default: return true;
                }
                // 重置到1级，模拟升级过程以确保装备匹配
                data.setLevel(1);
                data.setExp(0);
                data.setTotalExp(0);
                data.setPickaxeTier(com.godminer.model.PickaxeTier.WOOD);
                data.setEfficiencyLevel(1);
                data.setFortuneLevel(0);
                data.setUnbreakingLevel(1);
                data.setLevelsInCurrentTier(0);
                // 计算到达目标等级所需总经验并给予
                int totalExpNeeded = 0;
                for (int lv = 1; lv < newLevel; lv++) {
                    totalExpNeeded += lv * lv * 3;
                }
                data.setExp(totalExpNeeded);
                // 模拟自然升级
                simulateLevelUp(target, data);
                MessageUtil.sendMessage(sender, "&a已将 " + target.getName() + " 的等级从 " + oldLevel
                        + " 调整为 " + newLevel + "（镐子: " + data.getPickaxeTier().name() + "）");
                break;
            }
            default:
                MessageUtil.sendMessage(sender, "&c类型必须为 exp 或 lv！");
                return true;
        }

        plugin.getBossBarManager().updateBossBar(target);
        return true;
    }

    /**
     * 模拟自然升级过程，每升一级调用一次镐子升级检查，确保装备与等级匹配
     */
    private void simulateLevelUp(Player player, PlayerData data) {
        int maxIter = 500;
        int iter = 0;
        boolean anyUpgrade = false;

        while (data.canLevelUp() && iter < maxIter) {
            data.levelUp(); // 这会递增 levelsInCurrentTier
            // 每次升级后检查镐子
            String result = plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
            if (result != null) {
                anyUpgrade = true;
            }
            iter++;
        }

        // 升级完成后更新镐子和装备
        if (anyUpgrade || true) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                var item = inv.getItem(i);
                if (item != null && item.getItemMeta() != null && item.getItemMeta().isUnbreakable()
                        && item.getType().toString().endsWith("_PICKAXE")) {
                    inv.setItem(i, null);
                    break;
                }
            }
            inv.setItem(0, plugin.getPickaxeManager().createPickaxe(data));
            if (data.isSuitVisible()) {
                plugin.getPickaxeManager().equipArmor(player, data);
            }
        }
    }

    private void processLevelUp(Player player, PlayerData data) {
        // 保留兼容旧的调用方式
        simulateLevelUp(player, data);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                 @NotNull String label, @NotNull String[] args) {
        // 第1个参数：子命令
        if (args.length == 1) {
            List<String> cmds = new ArrayList<>(List.of("join", "shop", "info", "buy", "suit", "team", "top", "exchange", "help", "reload"));
            if (sender.hasPermission("goldminer.admin")) {
                cmds.addAll(List.of("set", "add", "remove"));
            }
            return filterPrefix(cmds, args[0]);
        }

        String sub = args[0].toLowerCase();

        // 第2个参数：各子命令的具体参数
        if (args.length == 2) {
            return switch (sub) {
                case "team" -> filterPrefix(List.of("create", "join", "accept", "leave", "list"), args[1]);
                case "buy" -> filterPrefix(List.of("lv"), args[1]);
                case "exchange" -> filterPrefix(List.of("10", "100", "1000"), args[1]);
                case "shop" -> {
                    if (sender.hasPermission("goldminer.admin"))
                        yield filterPrefix(List.of("set"), args[1]);
                    yield Collections.emptyList();
                }
                case "reload" -> {
                    if (sender.hasPermission("goldminer.admin"))
                        yield filterPrefix(List.of("pool", "info"), args[1]);
                    yield Collections.emptyList();
                }
                case "set", "add", "remove" -> {
                    if (sender.hasPermission("goldminer.admin"))
                        yield filterPrefix(List.of("exp", "lv"), args[1]);
                    yield Collections.emptyList();
                }
                default -> Collections.emptyList();
            };
        }

        // 第3个参数
        if (args.length == 3) {
            switch (sub) {
                case "team": {
                    String action = args[1].toLowerCase();
                    if (action.equals("join")) {
                        return filterPrefix(plugin.getTeamManager().getAllTeams().stream()
                                .map(Team::getName).toList(), args[2]);
                    }
                    if (action.equals("accept")) {
                        return filterPrefix(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName).toList(), args[2]);
                    }
                    break;
                }
                case "buy": {
                    if (args[1].equalsIgnoreCase("lv")) {
                        return filterPrefix(List.of("1", "5", "10"), args[2]);
                    }
                    break;
                }
                case "shop": {
                    if (args[1].equalsIgnoreCase("set") && sender.hasPermission("goldminer.admin")) {
                        List<String> keys = new ArrayList<>();
                        var shopConfig = plugin.getShopManager().getShopConfig();
                        collectKeys(shopConfig, "", keys);
                        return filterPrefix(keys, args[2]);
                    }
                    break;
                }
                case "set", "add", "remove": {
                    if (sender.hasPermission("goldminer.admin")) {
                        return filterPrefix(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName).toList(), args[2]);
                    }
                    break;
                }
            }
        }

        // 第4个参数：buy lv <amount> confirm
        if (args.length == 4 && sub.equals("buy") && args[1].equalsIgnoreCase("lv")) {
            return filterPrefix(List.of("confirm"), args[3]);
        }

        // admin命令第4个参数：set/add/remove exp|lv <player> <amount>
        if (args.length == 4 && sender.hasPermission("goldminer.admin")) {
            if (sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
                return filterPrefix(List.of("1", "10", "100"), args[3]);
            }
            if (sub.equals("shop") && args[1].equalsIgnoreCase("set")) {
                return filterPrefix(List.of("100", "500", "1000"), args[3]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(Collection<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }

    private void collectKeys(org.bukkit.configuration.ConfigurationSection section, String path, List<String> keys) {
        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                collectKeys(section.getConfigurationSection(key), fullPath, keys);
            } else if (section.get(key) instanceof Number) {
                keys.add(fullPath);
            }
        }
    }
}
