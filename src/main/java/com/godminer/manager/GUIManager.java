package com.godminer.manager;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.util.ItemBuilder;
import com.godminer.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * GUI菜单管理器
 */
public class GUIManager implements Listener {

    private final GoldMiner plugin;
    private final Map<UUID, String> menuContexts;
    private final Map<UUID, PotionSelection> potionSelections;
    private final Map<UUID, Integer> chainCardSelections; // 连锁卡范围选择暂存
    private final Map<UUID, Integer> chainCardHeightSelections; // 连锁卡高度选择暂存

    public GUIManager(GoldMiner plugin) {
        this.plugin = plugin;
        this.menuContexts = new HashMap<>();
        this.potionSelections = new HashMap<>();
        this.chainCardSelections = new HashMap<>();
        this.chainCardHeightSelections = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private record PotionSelection(int level, int durationIndex) {}

    // ==================== 主菜单 ====================

    public void openMainMenu(Player player) {
        String title = MessageUtil.colorizeString(
                plugin.getLangConfig().getString("gui.main-menu", "&6矿场菜单"));
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        // 0-返回主城
        inv.setItem(10, new ItemBuilder(Material.COMPASS)
                .name(plugin.getLangConfig().getString("gui.button.return-spawn.name", "&a返回主城"))
                .lore(plugin.getLangConfig().getString("gui.button.return-spawn.lore1", "&7点击返回主城"))
                .build());

        // 1-创建小队
        inv.setItem(11, new ItemBuilder(Material.EMERALD)
                .name(plugin.getLangConfig().getString("gui.button.create-team.name", "&b创建小队"))
                .lore(plugin.getLangConfig().getString("gui.button.create-team.lore1", "&7点击创建一个小队"))
                .build());

        // 2-排行榜
        inv.setItem(12, new ItemBuilder(Material.NETHER_STAR)
                .name("&6排行榜")
                .lore("&7点击查看矿场排行榜")
                .build());

        // 3-货币兑换
        inv.setItem(13, new ItemBuilder(Material.GOLD_INGOT)
                .name("&e货币兑换")
                .lore("&7点击兑换矿场金币")
                .build());

        // 4-矿场商店（新）
        inv.setItem(14, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&l矿场商店")
                .lore("&7购买增幅、等级等")
                .build());

        // 5-我的矿工信息（新）
        inv.setItem(15, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&b&l我的矿工信息")
                .lore("&7查看你的矿工数据")
                .build());

        // 6-装备切换
        inv.setItem(16, new ItemBuilder(Material.DIAMOND_CHESTPLATE)
                .name(plugin.getLangConfig().getString("gui.button.suit-toggle.name", "&d装备切换"))
                .lore(plugin.getLangConfig().getString("gui.button.suit-toggle.lore1", "&7点击切换装备显示/隐藏"))
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "main");
    }

    // ==================== 矿场商店主菜单 ====================

    public void openShopMenu(Player player) {
        String title = MessageUtil.colorizeString("&a&l矿场商店");
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        inv.setItem(11, new ItemBuilder(Material.POTION)
                .name("&d&l购买增幅")
                .lore("&7药水效果 / 暴击属性")
                .build());

        inv.setItem(13, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("&e&l购买等级")
                .lore("&7使用金币购买等级")
                .build());

        inv.setItem(15, new ItemBuilder(Material.IRON_BARS)
                .name("&6&l连锁体验卡")
                .lore("&7限时连锁挖矿（矿场内）")
                .build());

        inv.setItem(40, new ItemBuilder(Material.NETHER_STAR)
                .name("&d&l一般连锁")
                .lore("&7全地图连锁挖矿（矿场外）",
                        "&7价格: &6100 金币",
                        "&7时长: &e3小时")
                .build());

        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&c返回主菜单")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "shop_main");
    }

    // ==================== 增幅菜单 ====================

    public void openBoostMenu(Player player) {
        String title = MessageUtil.colorizeString("&d&l购买增幅");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        inv.setItem(11, new ItemBuilder(Material.POTION)
                .name("&a&l购买药水效果")
                .lore("&7急迫 / 速度 / 幸运")
                .build());

        inv.setItem(13, new ItemBuilder(Material.BLAZE_POWDER)
                .name("&c&l购买爆率和暴击倍率")
                .lore("&7永久提升暴击属性")
                .build());

        inv.setItem(15, new ItemBuilder(Material.BARRIER)
                .name("&c返回商店")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "boost_main");
    }

    // ==================== 药水商店 ====================

    public void openPotionShop(Player player) {
        String title = MessageUtil.colorizeString("&a&l药水商店");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        inv.setItem(11, new ItemBuilder(Material.GOLDEN_PICKAXE)
                .name("&e急迫 Haste")
                .lore("&7提高挖掘速度")
                .build());

        inv.setItem(13, new ItemBuilder(Material.SUGAR)
                .name("&b速度 Speed")
                .lore("&7提高移动速度")
                .build());

        inv.setItem(15, new ItemBuilder(Material.RABBIT_FOOT)
                .name("&a幸运 Luck")
                .lore("&7提高幸运属性")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "potion_shop");
    }

    // ==================== 具体药水购买界面 ====================

    public void openPotionBuyMenu(Player player, String potionType) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String displayName = ShopManager.getPotionDisplayName(potionType);
        String title = MessageUtil.colorizeString("&a购买" + displayName);
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        List<Integer> durations = plugin.getShopManager().getPotionDurations();
        int maxLevel = plugin.getShopManager().getMaxPotionLevel();

        // 获取当前选择
        PotionSelection sel = potionSelections.getOrDefault(player.getUniqueId(), new PotionSelection(1, 0));
        int selLevel = sel.level();
        int selDurIdx = sel.durationIndex();

        // 等级选择行 (slot 0-8)
        int[] showLevels = {1, 5, 10, 15, 20, 25, 30};
        for (int i = 0; i < showLevels.length && i < 9; i++) {
            int lv = Math.min(showLevels[i], maxLevel);
            boolean selected = (lv == selLevel);
            Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            int price = plugin.getShopManager().calculatePotionPrice(potionType, lv, selDurIdx);
            inv.setItem(i, new ItemBuilder(mat)
                    .name((selected ? "&a&l▶ " : "&7") + "Lv." + lv + (selected ? " &a&l◀" : ""))
                    .lore("&7药水等级: &e" + lv + "级",
                            "&7预估价格: &6" + MessageUtil.formatNumber(price),
                            selected ? "&a当前已选中" : "&7点击选择此等级")
                    .build());
        }

        // 时长选择行 (slot 9+)
        for (int i = 0; i < durations.size() && i < 9; i++) {
            int dur = durations.get(i);
            boolean selected = (i == selDurIdx);
            Material mat = selected ? Material.CLOCK : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            int price = plugin.getShopManager().calculatePotionPrice(potionType, selLevel, i);
            inv.setItem(i + 9, new ItemBuilder(mat)
                    .name((selected ? "&b&l▶ " : "&7") + ShopManager.formatDuration(dur) + (selected ? " &b&l◀" : ""))
                    .lore("&7持续时长: &e" + ShopManager.formatDuration(dur),
                            "&7预估价格: &6" + MessageUtil.formatNumber(price),
                            selected ? "&b当前已选中" : "&7点击选择此时长")
                    .build());
        }

        // 确认购买按钮 (slot 22)
        int finalPrice = plugin.getShopManager().calculatePotionPrice(potionType, selLevel, selDurIdx);
        int selDur = durations.get(Math.min(selDurIdx, durations.size() - 1));
        inv.setItem(22, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6&l确认购买")
                .lore("&7药水效果: &e" + displayName,
                        "&7等级: &eLv." + selLevel,
                        "&7时长: &e" + ShopManager.formatDuration(selDur),
                        "&7价格: &6" + MessageUtil.formatNumber(finalPrice) + " 金币",
                        "",
                        "&a点击确认购买！")
                .build());

        // 当前状态 (slot 40)
        int currentLevel = 0;
        int currentRemaining = 0;
        if (data.hasActivePotionEffect(potionType)) {
            var eff = data.getPotionEffect(potionType);
            currentLevel = eff.level;
            currentRemaining = eff.getRemainingSeconds();
        }
        inv.setItem(40, new ItemBuilder(Material.BOOK)
                .name("&e当前状态")
                .lore("&7等级: &e" + (currentLevel > 0 ? "Lv." + currentLevel : "无"),
                        "&7剩余: &e" + (currentRemaining > 0 ? ShopManager.formatDuration(currentRemaining) : "无"))
                .build());

        // 返回按钮 (slot 49)
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&c返回药水商店")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "potion_buy:" + potionType);
    }

    // ==================== 爆率和暴击倍率商店 ====================

    public void openCritShop(Player player) {
        String title = MessageUtil.colorizeString("&c&l暴击属性商店");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        inv.setItem(11, new ItemBuilder(Material.NETHER_STAR)
                .name("&6&l购买暴击率")
                .lore("&7永久提升挖矿暴击率")
                .build());

        inv.setItem(13, new ItemBuilder(Material.BLAZE_ROD)
                .name("&c&l购买暴击倍率")
                .lore("&7临时提升暴击倍率（30分钟）")
                .build());

        inv.setItem(15, new ItemBuilder(Material.BARRIER)
                .name("&c返回")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "crit_shop");
    }

    public void openCritRateShop(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String title = MessageUtil.colorizeString("&6购买暴击率");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        List<Double> tiers = plugin.getShopManager().getCritRateTiers();
        for (int i = 0; i < tiers.size(); i++) {
            double rate = tiers.get(i);
            int price = plugin.getShopManager().calculateCritRatePrice(rate);
            inv.setItem(10 + i, new ItemBuilder(Material.GOLD_NUGGET)
                    .name("&6暴击率 +" + ShopManager.formatRateDisplay(rate))
                    .lore("&7增加: &e" + ShopManager.formatRateDisplay(rate),
                            "&7价格: &6" + MessageUtil.formatNumber(price) + " 金币",
                            "&7当前暴击率: &e" + String.format("%.1f%%", data.getCritHitRate() * 100))
                    .build());
        }

        inv.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&c返回")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "crit_rate_shop");
    }

    public void openCritMagShop(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String title = MessageUtil.colorizeString("&c购买暴击倍率");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        List<Double> tiers = plugin.getShopManager().getCritMagTiers();
        for (int i = 0; i < tiers.size(); i++) {
            double mag = tiers.get(i);
            int price = plugin.getShopManager().calculateCritMagPrice(mag);
            inv.setItem(10 + i, new ItemBuilder(Material.BLAZE_POWDER)
                    .name("&c暴击倍率 x" + (int) mag)
                    .lore("&7增加: &e" + (int) mag + "倍",
                            "&7时长: &e30分钟",
                            "&7价格: &6" + MessageUtil.formatNumber(price) + " 金币",
                            "&7当前额外倍率: &e" + String.format("%.1f", data.getBonusCritMagnification()) + "倍",
                            "&7剩余时间: &e" + ShopManager.formatDuration(data.getCritMagRemainingSeconds()))
                    .build());
        }

        inv.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("&c返回")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "crit_mag_shop");
    }

    // ==================== 购买等级商店 ====================

    public void openLevelShop(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String title = MessageUtil.colorizeString("&e&l购买等级");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        int[] buyAmounts = {1, 5, 10};
        for (int i = 0; i < buyAmounts.length; i++) {
            int amount = buyAmounts[i];
            int price = plugin.getShopManager().calculateLevelPriceForAmount(data, amount);
            int targetLevel = data.getLevel() + amount;
            int expNeeded = data.getExpToTargetLevel(targetLevel);
            inv.setItem(10 + i, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                    .name("&e购买" + amount + "级")
                    .lore("&7当前等级: &eLv." + data.getLevel(),
                            "&7目标等级: &eLv." + targetLevel,
                            "&7所需经验: &a" + MessageUtil.formatNumber(expNeeded),
                            "&7价格: &6" + MessageUtil.formatNumber(price) + " 金币",
                            "&7公式: 所需经验 × 经验单价系数")
                    .build());
        }

        // 公式说明
        inv.setItem(22, new ItemBuilder(Material.OAK_SIGN)
                .name("&e购买等级公式")
                .lore("&7价格 = SUM(当前等级→目标等级",
                        "&7  每级所需经验) × 经验单价系数",
                        "&7",
                        "&a/goldminer buy lv <数量>",
                        "&7可购买任意数量等级")
                .build());

        inv.setItem(26, new ItemBuilder(Material.BARRIER)
                .name("&c返回商店")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "level_shop");
    }

    // ==================== 连锁体验卡商店 ====================

    public void openChainCardShop(Player player) {
        String title = MessageUtil.colorizeString("&6&l连锁体验卡");
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        inv.setItem(11, new ItemBuilder(Material.OAK_PLANKS)
                .name("&e平面X轴连锁")
                .lore("&7沿X轴扩展范围",
                        "&7最高15方块",
                        "&7时长: &e30秒")
                .build());

        inv.setItem(13, new ItemBuilder(Material.OAK_PLANKS)
                .name("&e平面Z轴连锁")
                .lore("&7沿Z轴扩展范围",
                        "&7最高15方块",
                        "&7时长: &e30秒")
                .build());

        inv.setItem(15, new ItemBuilder(Material.STONE)
                .name("&e半径范围连锁")
                .lore("&7以玩家为中心球形连锁",
                        "&7最高15半径",
                        "&7时长: &e30秒")
                .build());

        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&e视角方向连锁")
                .lore("&7视角前方直线连锁",
                        "&7最高15方块 × 最高15高度",
                        "&7时长: &e30秒")
                .build());

        // 当前状态
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null && data.hasChainCard()) {
            String cardName = ShopManager.getChainCardDisplayName(data.getChainCardType());
            inv.setItem(26, new ItemBuilder(Material.BOOK)
                    .name("&e当前连锁效果")
                    .lore("&7类型: &e" + cardName,
                            "&7范围: &e" + data.getChainCardBlocks(),
                            "&7高度: &e" + data.getChainCardHeight(),
                            "&7剩余: &e" + ShopManager.formatDuration(data.getChainCardRemainingSeconds()))
                    .build());
        }

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "chain_card_shop");
    }

    public void openChainCardRangeMenu(Player player, String cardType) {
        int maxBlocks = plugin.getShopManager().getChainCardMaxBlocks(cardType);
        String displayName = ShopManager.getChainCardDisplayName(cardType);
        boolean hasHeight = cardType.equals("ray");
        String title = MessageUtil.colorizeString("&6调配" + displayName);
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        int selBlocks = chainCardSelections.getOrDefault(player.getUniqueId(), 1);
        int selHeight = hasHeight ? chainCardHeightSelections.getOrDefault(player.getUniqueId(), 1) : 1;
        selBlocks = Math.max(1, Math.min(maxBlocks, selBlocks));
        selHeight = Math.max(1, Math.min(15, selHeight));
        chainCardSelections.put(player.getUniqueId(), selBlocks);
        if (hasHeight) chainCardHeightSelections.put(player.getUniqueId(), selHeight);

        int price = plugin.getShopManager().calculateChainCardPrice(cardType, selBlocks);

        // 范围行
        inv.setItem(10, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c◀ 减少范围").lore("&7当前: &e" + selBlocks + "方块").build());
        inv.setItem(13, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&6&l范围: " + selBlocks + " 方块").lore("&7点击箭头调整").build());
        inv.setItem(16, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a▶ 增加范围").lore("&7当前: &e" + selBlocks + "方块").build());

        // 高度行（仅视角方向连锁）
        if (hasHeight) {
            inv.setItem(28, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .name("&c◀ 减少高度").lore("&7当前高度: &e" + selHeight + "格").build());
            inv.setItem(31, new ItemBuilder(Material.DIAMOND_BLOCK)
                    .name("&b&l高度: " + selHeight + " 格").lore("&7从脚部向上延伸").build());
            inv.setItem(34, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .name("&a▶ 增加高度").lore("&7当前高度: &e" + selHeight + "格").build());
        }

        // 确认购买
        inv.setItem(40, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&l确认购买")
                .lore("&7类型: &e" + displayName,
                        "&7范围: &e" + selBlocks + "方块",
                        hasHeight ? "&7高度: &e" + selHeight + "格" : "",
                        "&7价格: &6" + MessageUtil.formatNumber(price) + " 金币",
                        "&7时长: &e30秒")
                .build());

        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("&c返回").build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "chain_card_range:" + cardType);
    }

    // 原地刷新范围界面（不关闭GUI）
    private void refreshChainCardRangeInPlace(Inventory inv, Player player, String cardType) {
        int selBlocks = chainCardSelections.getOrDefault(player.getUniqueId(), 1);
        boolean hasHeight = cardType.equals("ray");
        int selHeight = hasHeight ? chainCardHeightSelections.getOrDefault(player.getUniqueId(), 1) : 1;
        int maxBlocks = plugin.getShopManager().getChainCardMaxBlocks(cardType);
        String displayName = ShopManager.getChainCardDisplayName(cardType);
        selBlocks = Math.max(1, Math.min(maxBlocks, selBlocks));
        selHeight = Math.max(1, Math.min(15, selHeight));
        int price = plugin.getShopManager().calculateChainCardPrice(cardType, selBlocks);

        inv.setItem(10, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c◀ 减少范围").lore("&7当前: &e" + selBlocks + "方块").build());
        inv.setItem(13, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&6&l范围: " + selBlocks + " 方块").lore("&7点击箭头调整").build());
        inv.setItem(16, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a▶ 增加范围").lore("&7当前: &e" + selBlocks + "方块").build());
        if (hasHeight) {
            inv.setItem(28, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .name("&c◀ 减少高度").lore("&7当前高度: &e" + selHeight + "格").build());
            inv.setItem(31, new ItemBuilder(Material.DIAMOND_BLOCK)
                    .name("&b&l高度: " + selHeight + " 格").lore("&7从脚部向上延伸").build());
            inv.setItem(34, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .name("&a▶ 增加高度").lore("&7当前高度: &e" + selHeight + "格").build());
        }
        inv.setItem(40, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&l确认购买")
                .lore("&7类型: &e" + displayName,
                        "&7范围: &e" + selBlocks + "方块",
                        hasHeight ? "&7高度: &e" + selHeight + "格" : "",
                        "&7价格: &6" + MessageUtil.formatNumber(price) + " 金币",
                        "&7时长: &e30秒")
                .build());
    }

    // ==================== 点击事件处理 ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String context = menuContexts.get(player.getUniqueId());
        if (context == null) return;

        // 只处理顶部GUI库存的点击，玩家自身库存的点击也阻止（防止移动物品）
        event.setCancelled(true);

        // 必须是点击顶部库存才处理逻辑
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        switch (context) {
            case "main" -> handleMainClick(player, slot);
            case "exchange" -> handleExchangeClick(player, slot);
            case "shop_main" -> handleShopMainClick(player, slot);
            case "boost_main" -> handleBoostMainClick(player, slot);
            case "potion_shop" -> handlePotionShopClick(player, slot);
            case "crit_shop" -> handleCritShopClick(player, slot);
            case "crit_rate_shop" -> handleCritRateShopClick(player, data, slot);
            case "crit_mag_shop" -> handleCritMagShopClick(player, data, slot);
            case "level_shop" -> handleLevelShopClick(player, data, slot);
            case "chain_card_shop" -> handleChainCardShopClick(player, slot);
            default -> {
                if (context.startsWith("potion_buy:")) {
                    handlePotionBuyClick(player, data, context.substring(11), slot);
                } else if (context.startsWith("chain_card_range:")) {
                    handleChainCardRangeClick(player, data, context.substring(17), slot);
                }
            }
        }
    }

    // --- 主菜单点击 ---
    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 10: // 返回主城
                player.closeInventory();
                player.performCommand(plugin.getConfig().getString("menu.return-spawn.command", "warp"));
                break;
            case 11: // 创建小队
                player.closeInventory();
                player.performCommand("goldminer team create");
                break;
            case 12: // 排行榜
                player.closeInventory();
                player.performCommand("goldminer top");
                break;
            case 13: // 货币兑换
                player.closeInventory();
                openExchangeMenu(player);
                break;
            case 14: // 矿场商店
                openShopMenu(player);
                break;
            case 15: // 我的矿工信息
                player.closeInventory();
                player.performCommand("goldminer info");
                break;
            case 16: // 装备切换
                player.closeInventory();
                player.performCommand("goldminer suit");
                break;
        }
    }

    // --- 兑换菜单点击 ---
    private void handleExchangeClick(Player player, int slot) {
        boolean success = false;
        switch (slot) {
            case 11: success = plugin.getEconomyManager().exchangeCoins(player, 10); break;
            case 13: success = plugin.getEconomyManager().exchangeCoins(player, 100); break;
            case 15: success = plugin.getEconomyManager().exchangeCoins(player, 1000); break;
            case 22:
                player.closeInventory();
                MessageUtil.sendMessage(player, plugin.getLangConfig().getString("exchange.prompt-amount", "&a请输入你要兑换的金币数量："));
                break;
        }
        if (success) {
            player.closeInventory();
            openExchangeMenu(player);
        }
    }

    // --- 商店主菜单 ---
    private void handleShopMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openBoostMenu(player);
            case 13 -> openLevelShop(player);
            case 15 -> openChainCardShop(player);
            case 40 -> {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (data != null) {
                    plugin.getShopManager().buyGlobalChain(player, data);
                }
                player.closeInventory();
                openShopMenu(player);
            }
            case 49 -> openMainMenu(player);
        }
    }

    // --- 增幅菜单 ---
    private void handleBoostMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openPotionShop(player);
            case 13 -> openCritShop(player);
            case 15 -> openShopMenu(player);
        }
    }

    // --- 药水商店 ---
    private void handlePotionShopClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openPotionBuyMenu(player, "haste");
            case 13 -> openPotionBuyMenu(player, "speed");
            case 15 -> openPotionBuyMenu(player, "luck");
        }
    }

    // --- 药水购买界面 ---
    private void handlePotionBuyClick(Player player, PlayerData data, String potionType, int slot) {
        List<Integer> durations = plugin.getShopManager().getPotionDurations();
        int maxLevel = plugin.getShopManager().getMaxPotionLevel();
        int[] showLevels = {1, 5, 10, 15, 20, 25, 30};

        PotionSelection sel = potionSelections.getOrDefault(player.getUniqueId(), new PotionSelection(1, 0));

        // 点击等级行 (slot 0-6)
        if (slot >= 0 && slot < showLevels.length) {
            int lv = Math.min(showLevels[slot], maxLevel);
            potionSelections.put(player.getUniqueId(), new PotionSelection(lv, sel.durationIndex()));
            player.closeInventory();
            openPotionBuyMenu(player, potionType);
            return;
        }

        // 点击时长行 (slot 9 起始)
        int durSlot = slot - 9;
        if (durSlot >= 0 && durSlot < durations.size()) {
            potionSelections.put(player.getUniqueId(), new PotionSelection(sel.level(), durSlot));
            player.closeInventory();
            openPotionBuyMenu(player, potionType);
            return;
        }

        // 确认购买按钮 (slot 22)
        if (slot == 22) {
            int durIdx = Math.min(sel.durationIndex(), durations.size() - 1);
            int dur = durations.get(durIdx);
            plugin.getShopManager().buyPotionEffect(player, data, potionType, sel.level(), dur);
            player.closeInventory();
            openPotionBuyMenu(player, potionType);
            return;
        }

        // 返回 (slot 49)
        if (slot == 49) {
            potionSelections.remove(player.getUniqueId());
            openPotionShop(player);
        }
    }

    // --- 暴击商店 ---
    private void handleCritShopClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openCritRateShop(player);
            case 13 -> openCritMagShop(player);
            case 15 -> openBoostMenu(player);
        }
    }

    private void handleCritRateShopClick(Player player, PlayerData data, int slot) {
        List<Double> tiers = plugin.getShopManager().getCritRateTiers();
        int idx = slot - 10;
        if (idx >= 0 && idx < tiers.size()) {
            double rate = tiers.get(idx);
            plugin.getShopManager().buyCritRate(player, data, rate);
        } else if (slot == 22) {
            openCritShop(player);
        }
    }

    private void handleCritMagShopClick(Player player, PlayerData data, int slot) {
        List<Double> tiers = plugin.getShopManager().getCritMagTiers();
        int idx = slot - 10;
        if (idx >= 0 && idx < tiers.size()) {
            double mag = tiers.get(idx);
            plugin.getShopManager().buyCritMagnification(player, data, mag);
        } else if (slot == 22) {
            openCritShop(player);
        }
    }

    // --- 等级购买 ---
    private void handleLevelShopClick(Player player, PlayerData data, int slot) {
        int[] buyAmounts = {1, 5, 10};
        int idx = slot - 10;
        if (idx >= 0 && idx < buyAmounts.length) {
            plugin.getShopManager().buyLevels(player, data, buyAmounts[idx]);
            player.closeInventory();
            openLevelShop(player);
        } else if (slot == 26) {
            openShopMenu(player);
        }
    }

    // --- 连锁体验卡商店 ---
    private void handleChainCardShopClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openChainCardRangeMenu(player, "plane_x");
            case 13 -> openChainCardRangeMenu(player, "plane_z");
            case 15 -> openChainCardRangeMenu(player, "radius");
            case 22 -> openChainCardRangeMenu(player, "ray");
        }
    }

    // --- 连锁卡范围选择（原地刷新，不关闭GUI） ---
    private void handleChainCardRangeClick(Player player, PlayerData data, String cardType, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int max = plugin.getShopManager().getChainCardMaxBlocks(cardType);
        int sel = chainCardSelections.getOrDefault(player.getUniqueId(), 1);
        boolean hasHeight = cardType.equals("ray");
        int selH = hasHeight ? chainCardHeightSelections.getOrDefault(player.getUniqueId(), 1) : 1;

        switch (slot) {
            case 10 -> { if (sel > 1) chainCardSelections.put(player.getUniqueId(), sel - 1); refreshChainCardRangeInPlace(inv, player, cardType); }
            case 16 -> { if (sel < max) chainCardSelections.put(player.getUniqueId(), sel + 1); refreshChainCardRangeInPlace(inv, player, cardType); }
            case 28 -> { if (hasHeight && selH > 1) { chainCardHeightSelections.put(player.getUniqueId(), selH - 1); refreshChainCardRangeInPlace(inv, player, cardType); } }
            case 34 -> { if (hasHeight && selH < 15) { chainCardHeightSelections.put(player.getUniqueId(), selH + 1); refreshChainCardRangeInPlace(inv, player, cardType); } }
            case 40 -> {
                plugin.getShopManager().buyChainCard(player, data, cardType, sel, selH);
                chainCardSelections.remove(player.getUniqueId());
                chainCardHeightSelections.remove(player.getUniqueId());
                player.closeInventory();
                openChainCardShop(player);
            }
            case 49 -> {
                chainCardSelections.remove(player.getUniqueId());
                chainCardHeightSelections.remove(player.getUniqueId());
                player.closeInventory();
                openChainCardShop(player);
            }
        }
    }

    // ==================== 其他菜单 ====================

    public void openExchangeMenu(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String title = MessageUtil.colorizeString(
                plugin.getLangConfig().getString("gui.exchange-menu", "&6金币兑换"));
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(title));

        inv.setItem(4, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&e当前矿场金币: &6" + MessageUtil.formatNumber(data.getCoins()))
                .lore("&7汇率: 1矿场金币 = " +
                        plugin.getConfig().getDouble("currency.exchange-rate", 100) + "主世界货币")
                .build());

        inv.setItem(11, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6兑换 10 金币")
                .lore("&7获得 " + MessageUtil.formatNumber(
                        (int)(10 * plugin.getConfig().getDouble("currency.exchange-rate", 100))) + " 主世界货币")
                .build());

        inv.setItem(13, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&6兑换 100 金币")
                .lore("&7获得 " + MessageUtil.formatNumber(
                        (int)(100 * plugin.getConfig().getDouble("currency.exchange-rate", 100))) + " 主世界货币")
                .build());

        inv.setItem(15, new ItemBuilder(Material.ENCHANTED_GOLDEN_APPLE)
                .name("&6兑换 1000 金币")
                .lore("&7获得 " + MessageUtil.formatNumber(
                        (int)(1000 * plugin.getConfig().getDouble("currency.exchange-rate", 100))) + " 主世界货币")
                .build());

        inv.setItem(22, new ItemBuilder(Material.OAK_SIGN)
                .name("&a自定义兑换数量")
                .lore("&7使用 &e/goldminer exchange <数量> &7自定义兑换")
                .build());

        player.openInventory(inv);
        menuContexts.put(player.getUniqueId(), "exchange");
    }

    public ItemStack createMenuStar() {
        return new ItemBuilder(Material.NETHER_STAR)
                .name("&6矿场菜单 &7(右键打开)")
                .lore("&7右键点击打开矿场菜单")
                .build();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (menuContexts.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        menuContexts.remove(event.getPlayer().getUniqueId());
    }
}
