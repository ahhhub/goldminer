package com.godminer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * 消息工具类
 */
public class MessageUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * 将含&的颜色代码转换为Component
     */
    public static Component colorize(String message) {
        if (message == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(message);
    }

    /**
     * 将含&的颜色代码转换为纯文本字符串
     */
    public static String colorizeString(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * 发送带颜色的消息
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        sender.sendMessage(colorize(message));
    }

    /**
     * 发送带前缀的消息
     */
    public static void sendPrefixedMessage(CommandSender sender, String prefix, String message) {
        sendMessage(sender, prefix + message);
    }

    /**
     * 格式化数字（加千位分隔符）
     */
    public static String formatNumber(int number) {
        return String.format("%,d", number);
    }

    /**
     * 替换字符串中的占位符
     */
    public static String replacePlaceholders(String message, String... replacements) {
        if (message == null) return "";
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }
}
