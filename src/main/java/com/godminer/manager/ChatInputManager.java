package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.Team;
import com.godminer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天输入管理器 - 处理聊天栏交互
 */
public class ChatInputManager implements Listener {

    private final GoldMiner plugin;
    private final Map<UUID, ChatExpectation> expectedInputs;

    public ChatInputManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.expectedInputs = new ConcurrentHashMap<>();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 期望玩家在聊天栏输入
     */
    public void expectInput(Player player, String context) {
        expectedInputs.put(player.getUniqueId(), new ChatExpectation(context));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatExpectation expectation = expectedInputs.remove(player.getUniqueId());
        if (expectation == null) return;

        event.setCancelled(true);
        String message = event.getMessage();

        if (message.equalsIgnoreCase("C") || message.equalsIgnoreCase("取消")) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString(
                    "team.create-cancelled", "&c操作已取消！"));
            return;
        }

        switch (expectation.context()) {
            case "team_create":
                handleTeamCreateInput(player, message);
                break;
            case "exchange_amount":
                handleExchangeInput(player, message);
                break;
        }
    }

    private void handleTeamCreateInput(Player player, String teamName) {
        if (teamName.length() < 2 || teamName.length() > 16) {
            MessageUtil.sendMessage(player, "&c队伍名称长度必须在2-16个字符之间！");
            return;
        }

        if (!teamName.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            MessageUtil.sendMessage(player, "&c队伍名称只能包含字母、数字、下划线和中文！");
            return;
        }

        UUID uuid = player.getUniqueId();
        Team team = plugin.getTeamManager().createTeam(teamName, uuid);
        if (team == null) {
            MessageUtil.sendMessage(player, plugin.getLangConfig().getString(
                    "team.name-taken", "&c该小队名称已被使用或你已在队伍中！"));
            return;
        }

        String msg = plugin.getLangConfig().getString("team.create-success",
                "&a小队 &e{team} &a创建成功！你是队长。");
        msg = MessageUtil.replacePlaceholders(msg, "{team}", teamName);
        MessageUtil.sendMessage(player, msg);
    }

    private void handleExchangeInput(Player player, String message) {
        try {
            int amount = Integer.parseInt(message.trim());
            if (amount <= 0) {
                MessageUtil.sendMessage(player, "&c请输入有效的正整数！");
                return;
            }
            // 在同步线程执行
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getEconomyManager().exchangeCoins(player, amount);
            });
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "&c请输入有效的数字！");
        }
    }

    private record ChatExpectation(String context) {}
}
