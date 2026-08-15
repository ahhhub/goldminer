package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.model.Team;
import com.godminer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小队管理器
 */
public class TeamManager {

    private final GoldMiner plugin;
    private final Map<String, Team> teams; // 队名 -> 小队
    private final Map<UUID, String> playerTeams; // 玩家UUID -> 队名

    public TeamManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.teams = new ConcurrentHashMap<>();
        this.playerTeams = new ConcurrentHashMap<>();
    }

    /**
     * 创建小队
     */
    public Team createTeam(String name, UUID leaderUuid) {
        if (teams.containsKey(name.toLowerCase())) {
            return null; // 名称已存在
        }
        if (playerTeams.containsKey(leaderUuid)) {
            return null; // 玩家已有小队
        }

        Team team = new Team(name, leaderUuid);
        teams.put(name.toLowerCase(), team);
        playerTeams.put(leaderUuid, name.toLowerCase());

        // 设置玩家数据中的队伍名
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(leaderUuid);
        if (data != null) {
            data.setTeamName(name);
        }

        return team;
    }

    /**
     * 加入小队（需要队长接受）
     */
    public boolean applyToTeam(UUID applicantUuid, String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        if (team == null) return false;
        if (playerTeams.containsKey(applicantUuid)) return false; // 已有队伍

        int maxMembers = plugin.getConfig().getInt("team.max-members", 10);
        if (team.getSize() >= maxMembers) return false;

        // 添加到待处理申请
        team.addPendingApplication(applicantUuid);

        // 通知队长
        Player leader = Bukkit.getPlayer(team.getLeaderUuid());
        String applicantName = Bukkit.getOfflinePlayer(applicantUuid).getName();

        if (leader != null && leader.isOnline()) {
            String msg = plugin.getLangConfig().getString("team.application-received",
                    "&e你有一个入队申请，玩家 &b{player} &e希望加入你的队伍。");
            msg = MessageUtil.replacePlaceholders(msg, "{player}", applicantName);
            MessageUtil.sendMessage(leader, msg);
        } else {
            // 队长不在线，暂存消息
            team.addOfflinePendingMessage(applicantUuid,
                    "入队申请: " + applicantName);
        }

        return true;
    }

    /**
     * 队长接受入队申请
     */
    public boolean acceptApplication(UUID leaderUuid, UUID applicantUuid) {
        Team team = getTeamByLeader(leaderUuid);
        if (team == null) return false;

        if (!team.hasPendingApplication(applicantUuid)) return false;

        team.addMember(applicantUuid);
        playerTeams.put(applicantUuid, team.getName().toLowerCase());

        // 设置玩家数据
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(applicantUuid);
        if (data != null) {
            data.setTeamName(team.getName());
            // 应用队伍最高等级
            applyTeamLevels(team, data);
        }

        return true;
    }

    /**
     * 队长接受所有入队申请
     */
    public int acceptAllApplications(UUID leaderUuid) {
        Team team = getTeamByLeader(leaderUuid);
        if (team == null) return 0;

        Set<UUID> pending = new HashSet<>(team.getPendingApplications());
        int count = 0;
        for (UUID applicantUuid : pending) {
            if (acceptApplication(leaderUuid, applicantUuid)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 退出小队 - 清空经验和镐子进度
     */
    public boolean leaveTeam(UUID playerUuid) {
        String teamName = playerTeams.remove(playerUuid);
        if (teamName == null) return false;

        Team team = teams.get(teamName);
        if (team == null) return false;

        boolean wasLeader = team.isLeader(playerUuid);
        team.removeMember(playerUuid);

        // 更新玩家数据 - 重置所有进度但保留金币
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerUuid);
        if (data != null) {
            data.setTeamName(null);
            data.resetProgress(); // 重置等级/经验/镐子等级

            // 重置在线玩家的镐子和装备
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // 重置镐子为初始木镐
                player.getInventory().setItem(0, plugin.getPickaxeManager().createInitialPickaxe());
                // 清除装备
                plugin.getPickaxeManager().clearArmor(player);
                // 更新BossBar
                plugin.getBossBarManager().updateBossBar(player);
            }
        }

        // 如果队长退出且队伍还有成员，转移队长
        if (wasLeader && !team.getMembers().isEmpty()) {
            UUID newLeader = team.getMembers().iterator().next();
            // 创建新队伍（简化处理：转移队长）
            Team newTeam = new Team(team.getName(), newLeader);
            for (UUID member : team.getMembers()) {
                newTeam.addMember(member);
                playerTeams.put(member, team.getName().toLowerCase());
            }
            teams.put(team.getName().toLowerCase(), newTeam);
        } else if (team.getMembers().isEmpty()) {
            teams.remove(teamName);
        }

        return true;
    }

    /**
     * 获取玩家所在小队
     */
    public Team getTeamByPlayer(UUID playerUuid) {
        String teamName = playerTeams.get(playerUuid);
        if (teamName == null) return null;
        return teams.get(teamName);
    }

    /**
     * 获取队长的小队
     */
    public Team getTeamByLeader(UUID leaderUuid) {
        String teamName = playerTeams.get(leaderUuid);
        if (teamName == null) return null;
        Team team = teams.get(teamName);
        if (team != null && team.isLeader(leaderUuid)) {
            return team;
        }
        return null;
    }

    /**
     * 获取小队
     */
    public Team getTeam(String name) {
        return teams.get(name.toLowerCase());
    }

    /**
     * 获取所有小队列表
     */
    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    /**
     * 应用队伍最高等级和附魔给成员
     */
    public void applyTeamLevels(Team team, PlayerData memberData) {
        // 找到队伍中等级最高的成员
        PlayerData maxData = null;
        int maxLevel = 0;

        for (UUID memberUuid : team.getMembers()) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUuid);
            if (data != null && data.getLevel() > maxLevel) {
                maxLevel = data.getLevel();
                maxData = data;
            }
        }

        if (maxData != null) {
            memberData.applyTeamMax(maxData);
        }
    }

    /**
     * 更新队伍所有成员的等级（当有人升级时）
     */
    public void syncTeamLevels(Team team) {
        if (team == null) return;

        PlayerData maxData = null;
        int maxLevel = 0;

        for (UUID memberUuid : team.getMembers()) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUuid);
            if (data != null && data.getLevel() > maxLevel) {
                maxLevel = data.getLevel();
                maxData = data;
            }
        }

        if (maxData != null) {
            for (UUID memberUuid : team.getMembers()) {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUuid);
                if (data != null && data.getLevel() < maxLevel) {
                    data.applyTeamMax(maxData);

                    // 更新在线玩家的镐子
                    Player player = Bukkit.getPlayer(memberUuid);
                    if (player != null && player.isOnline()) {
                        plugin.getPickaxeManager().checkAndUpgradePickaxe(data);
                    }
                }
            }
        }
    }

    /**
     * 向玩家发送离线期间存储的入队通知
     */
    public void sendOfflineNotifications(Player player) {
        Team team = getTeamByLeader(player.getUniqueId());
        if (team == null) return;

        Map<UUID, String> messages = team.getOfflinePendingMessages();
        if (!messages.isEmpty()) {
            for (String msg : messages.values()) {
                MessageUtil.sendMessage(player, "&e[离线通知] " + msg);
            }
            team.clearOfflinePendingMessages();
        }
    }
}
