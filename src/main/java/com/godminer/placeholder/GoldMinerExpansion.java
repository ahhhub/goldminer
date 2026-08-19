package com.godminer.placeholder;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GoldMiner PlaceholderAPI 扩展
 * 提供占位符: %goldminer_reload_time%, %goldminer_user_level%, %goldminer_user_money%
 */
public class GoldMinerExpansion extends PlaceholderExpansion {

    private final GoldMiner plugin;

    public GoldMinerExpansion(GoldMiner plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "goldminer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "GoldMinerTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        // 不需要玩家参数的占位符
        switch (identifier.toLowerCase()) {
            case "reload_time":
                int interval = plugin.getConfig().getInt("mine.check-interval", 10);
                return String.valueOf(interval);
        }

        // 需要玩家参数的占位符
        if (offlinePlayer == null) return null;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(offlinePlayer.getUniqueId());
        if (data == null) return "0";

        switch (identifier.toLowerCase()) {
            case "user_level":
                return String.valueOf(data.getLevel());

            case "user_money":
                return String.valueOf(data.getCoins());

            case "crit_hit_rate":
                return String.format("%.1f", data.getCritHitRate() * 100);

            case "crit_magnification":
                return String.format("%.1f", data.getEffectiveCritMagnification());

            case "crit_time":
                return String.valueOf(data.getCritMagRemainingSeconds());

            case "interlocking_type":
                if (!data.hasChainCard()) return "无";
                return com.godminer.manager.ShopManager.getChainCardDisplayName(data.getChainCardType());

            case "interlocking_time":
                return String.valueOf(data.getChainCardRemainingSeconds());

            case "nomal_interlocking_time":
                return String.valueOf(data.getGlobalChainRemainingSeconds());

            default:
                return null;
        }
    }
}
