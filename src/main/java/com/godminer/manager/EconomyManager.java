package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济管理器 - 管理矿场金币和主世界货币兑换
 */
public class EconomyManager {

    private final GoldMiner plugin;
    private Economy vaultEconomy;
    private boolean vaultEnabled;

    public EconomyManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vaultEconomy = rsp.getProvider();
                vaultEnabled = true;
                plugin.getLogger().info("Vault 经济系统已连接。");
                return true;
            }
        }
        vaultEnabled = false;
        plugin.getLogger().warning("未找到 Vault 经济系统，货币兑换功能将不可用。");
        return false;
    }

    /**
     * 给玩家矿场金币
     */
    public void addCoins(Player player, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null) {
            data.addCoins(amount);
        }
    }

    /**
     * 获取玩家矿场金币
     */
    public int getCoins(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return data != null ? data.getCoins() : 0;
    }

    /**
     * 将矿场金币兑换为主世界货币
     */
    public boolean exchangeCoins(Player player, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return false;

        if (!data.removeCoins(amount)) {
            return false; // 金币不足
        }

        double rate = plugin.getConfig().getDouble("currency.exchange-rate", 100.0);
        double converted = amount * rate;

        if (vaultEnabled && vaultEconomy != null) {
            vaultEconomy.depositPlayer(player, converted);
        }

        String msg = plugin.getLangConfig().getString("exchange.success", "&a成功兑换 &6{amount} &a矿场金币为 &6{converted} &a主世界货币！");
        msg = MessageUtil.replacePlaceholders(msg,
                "{amount}", String.valueOf(amount),
                "{converted}", MessageUtil.formatNumber((int) converted));
        MessageUtil.sendMessage(player, msg);

        return true;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public Economy getVaultEconomy() {
        return vaultEconomy;
    }
}
