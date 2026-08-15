package com.godminer.listener;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 玩家事件监听器
 * 处理物品保护、玻璃补充、药水效果、盔甲保护
 */
public class PlayerListener implements Listener {

    private final GoldMiner plugin;

    public PlayerListener(GoldMiner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        plugin.getTeamManager().sendOfflineNotifications(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null && data.getCurrentMineWorld() != null) {
            plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
            var mainWorld = Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
            data.setCurrentMineWorld(null);
            // 移除药水效果
            removeMineEffects(player);
        }
        plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
        plugin.getBossBarManager().removeBossBar(player);
    }

    /**
     * 保护快捷栏 0-2 格和盔甲栏
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        int slot = event.getSlot();
        // 保护盔甲栏 (slot 5-8 in inventory view = armor slots)
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }

        // 检查是否点击了盔甲栏
        if (event.getRawSlot() >= 5 && event.getRawSlot() <= 8
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            event.setCancelled(true);
            return;
        }

        // 保护第一格（镐子）、第二格（下界之星）、第三格（玻璃）
        if (slot >= 0 && slot <= 2 && isProtectedSlot(player, slot)) {
            event.setCancelled(true);
            return;
        }

        // 防止数字键切换物品到保护槽位
        if (event.getClick().toString().contains("NUMBER_KEY")) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        // 检查拖拽是否涉及保护槽位
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot <= 2) {
                event.setCancelled(true);
                return;
            }
            if (slot >= 36 && slot <= 39) { // 盔甲栏
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 补充玻璃：当玩家用掉玻璃时自动补充
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        ItemStack placed = event.getItemInHand();
        String glassMat = plugin.getConfig().getString("glass-block.material", "GLASS");
        if (placed.getType().name().equalsIgnoreCase(glassMat) && isGlassItem(placed)) {
            // 延迟补充玻璃
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack slot2 = player.getInventory().getItem(2);
                if (slot2 == null || slot2.getType() == Material.AIR) {
                    player.getInventory().setItem(2, plugin.getPickaxeManager().createGlass());
                } else if (slot2.getType() == Material.GLASS && slot2.getAmount() < 64) {
                    slot2.setAmount(64);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        ItemStack item = event.getItemDrop().getItemStack();
        if (isProtectedItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;
        if (!player.getWorld().getName().equals(data.getCurrentMineWorld())) return;

        // 保留保护物品
        event.getDrops().removeIf(this::isProtectedItem);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getCurrentMineWorld() == null) return;

        var world = Bukkit.getWorld(data.getCurrentMineWorld());
        if (world != null) {
            event.setRespawnLocation(plugin.getWorldManager().getSafeSpawnLocation(world));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ensureMineItems(player, data);
            }, 5L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(item.getItemMeta().displayName());
        if (displayName.contains("矿场菜单")) {
            event.setCancelled(true);
            plugin.getGUIManager().openMainMenu(player);
        }
    }

    /**
     * 进入矿场世界时应用药水效果
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String currentWorld = player.getWorld().getName();
        String mineWorld = data.getCurrentMineWorld();

        if (mineWorld != null && currentWorld.equals(mineWorld)) {
            // 进入矿场 - 应用效果 + 检查装备
            applyMineEffects(player);
            plugin.getBossBarManager().updateBossBar(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkAndReEquipArmor(player, data);
            }, 10L);
        } else if (mineWorld != null && !currentWorld.equals(mineWorld)) {
            // 离开矿场 - 移除效果，清除追踪
            removeMineEffects(player);
            plugin.getBossBarManager().removeBossBar(player);
            data.setCurrentMineWorld(null); // 清除追踪，允许后续重新join
        }
    }

    private void applyMineEffects(Player player) {
        if (plugin.getConfig().getBoolean("potion-effects.regeneration.enabled", true)) {
            int amp = plugin.getConfig().getInt("potion-effects.regeneration.amplifier", 4);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    PotionEffect.INFINITE_DURATION, amp, false, false, true));
        }
        if (plugin.getConfig().getBoolean("potion-effects.saturation.enabled", true)) {
            int amp = plugin.getConfig().getInt("potion-effects.saturation.amplifier", 4);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,
                    PotionEffect.INFINITE_DURATION, amp, false, false, true));
        }
        if (plugin.getConfig().getBoolean("potion-effects.resistance.enabled", true)) {
            int amp = plugin.getConfig().getInt("potion-effects.resistance.amplifier", 4);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    PotionEffect.INFINITE_DURATION, amp, false, false, true));
        }
    }

    private void removeMineEffects(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    /**
     * 确保玩家有镐子、菜单星、玻璃和装备
     */
    public void ensureMineItems(Player player, PlayerData data) {
        var inv = player.getInventory();

        // 镐子
        ItemStack pickaxe = inv.getItem(0);
        if (!isProtectedItem(pickaxe) || pickaxe == null) {
            inv.setItem(0, plugin.getPickaxeManager().createPickaxe(data));
        }

        // 菜单星
        ItemStack star = inv.getItem(1);
        if (star == null || star.getType() != Material.NETHER_STAR) {
            inv.setItem(1, plugin.getGUIManager().createMenuStar());
        }

        // 无限玻璃
        ItemStack glass = inv.getItem(2);
        if (glass == null || glass.getType() != Material.GLASS || glass.getAmount() < 64) {
            inv.setItem(2, plugin.getPickaxeManager().createGlass());
        }

        // 检查并重穿装备
        checkAndReEquipArmor(player, data);

        // 应用药水效果
        applyMineEffects(player);
    }

    /**
     * 检查玩家装备：如果没卸下但装备缺失，自动重穿
     */
    private void checkAndReEquipArmor(Player player, PlayerData data) {
        if (!data.isSuitVisible()) return; // 已手动卸下，不重穿
        if (!plugin.getPickaxeManager().hasCorrectArmor(player, data)) {
            plugin.getPickaxeManager().equipArmor(player, data);
        }
    }

    private boolean isProtectedSlot(Player player, int slot) {
        return slot == 0 || slot == 1 || slot == 2;
    }

    private boolean isGlassItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getType() != Material.GLASS) return false;
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        String name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(displayName);
        return name.contains("垫脚玻璃");
    }

    private boolean isProtectedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getItemMeta().isUnbreakable() && item.getType().toString().endsWith("_PICKAXE")) {
            return true;
        }
        if (isGlassItem(item)) return true;
        if (item.getType() == Material.NETHER_STAR && item.getItemMeta().hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(item.getItemMeta().displayName());
            if (name.contains("矿场菜单")) return true;
        }
        return false;
    }
}
