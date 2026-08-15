package com.godminer.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据模型
 */
public class PlayerData {
    private final UUID uuid;
    private String playerName;
    private int coins;
    private int exp;
    private int level;
    private int totalExp;
    private String currentMineWorld;
    private String teamName;
    private PickaxeTier pickaxeTier;
    private int efficiencyLevel;
    private int fortuneLevel;
    private int unbreakingLevel;
    private long lastSaveTime;
    private boolean suitVisible;
    private int levelsInCurrentTier;

    // 暴击系统
    private double critHitRate;          // 暴击率 (0.005 = 0.5%)
    private double critMagnification;    // 基础暴击倍率 (0.5)
    private double bonusCritMagnification; // 额外暴击倍率 (购买获得)
    private long bonusCritMagEndTime;    // 额外暴击倍率生效结束时间 (0=永久/未激活)

    // 药水效果 (效果类型 -> [等级, 结束时间])
    private final Map<String, PotionEffectData> activePotionEffects;

    // 连锁体验卡（矿场世界内生效）
    private String chainCardType;        // plane_x, plane_z, radius, ray 或 null
    private int chainCardBlocks;         // 连锁方块数/半径
    private long chainCardEndTime;       // 结束时间
    private int chainCardHeight;         // 连锁高度（从脚部向上延伸）
    private int rayChainMaxBlocks;       // 视角连锁最大方块数（仅ray类型）

    // 一般连锁（矿场世界外生效）
    private long globalChainEndTime;     // 全局连锁结束时间

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.coins = 0;
        this.exp = 0;
        this.level = 1;
        this.totalExp = 0;
        this.pickaxeTier = PickaxeTier.WOOD;
        this.efficiencyLevel = 0;
        this.fortuneLevel = 0;
        this.unbreakingLevel = 0;
        this.lastSaveTime = System.currentTimeMillis();
        this.suitVisible = true;
        this.levelsInCurrentTier = 0;

        // 暴击系统默认值
        this.critHitRate = 0.005;       // 0.5%
        this.critMagnification = 0.5;   // 0.5倍
        this.bonusCritMagnification = 0;
        this.bonusCritMagEndTime = 0;

        // 药水效果
        this.activePotionEffects = new ConcurrentHashMap<>();

        // 连锁体验卡
        this.chainCardType = null;
        this.chainCardBlocks = 0;
        this.chainCardEndTime = 0;
        this.rayChainMaxBlocks = 0;

        // 一般连锁
        this.globalChainEndTime = 0;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = Math.max(0, coins); }
    public void addCoins(int amount) { this.coins = Math.max(0, this.coins + amount); }
    public boolean removeCoins(int amount) {
        if (amount <= 0) return false;
        if (this.coins < amount) return false;
        this.coins -= amount;
        return true;
    }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }
    public void addExp(int amount) { this.exp += amount; this.totalExp += amount; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getTotalExp() { return totalExp; }
    public void setTotalExp(int totalExp) { this.totalExp = totalExp; }

    public String getCurrentMineWorld() { return currentMineWorld; }
    public void setCurrentMineWorld(String currentMineWorld) { this.currentMineWorld = currentMineWorld; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public PickaxeTier getPickaxeTier() { return pickaxeTier; }
    public void setPickaxeTier(PickaxeTier pickaxeTier) { this.pickaxeTier = pickaxeTier; }

    public int getEfficiencyLevel() { return efficiencyLevel; }
    public void setEfficiencyLevel(int efficiencyLevel) { this.efficiencyLevel = efficiencyLevel; }

    public int getFortuneLevel() { return fortuneLevel; }
    public void setFortuneLevel(int fortuneLevel) { this.fortuneLevel = fortuneLevel; }

    public int getUnbreakingLevel() { return unbreakingLevel; }
    public void setUnbreakingLevel(int unbreakingLevel) { this.unbreakingLevel = unbreakingLevel; }

    public long getLastSaveTime() { return lastSaveTime; }
    public void setLastSaveTime(long lastSaveTime) { this.lastSaveTime = lastSaveTime; }

    public boolean isSuitVisible() { return suitVisible; }
    public void setSuitVisible(boolean suitVisible) { this.suitVisible = suitVisible; }
    public void toggleSuit() { this.suitVisible = !this.suitVisible; }

    public int getLevelsInCurrentTier() { return levelsInCurrentTier; }
    public void setLevelsInCurrentTier(int levelsInCurrentTier) { this.levelsInCurrentTier = levelsInCurrentTier; }

    // ===== 暴击系统 =====
    public double getCritHitRate() { return critHitRate; }
    public void setCritHitRate(double critHitRate) { this.critHitRate = Math.min(1.0, Math.max(0, critHitRate)); }
    public void addCritHitRate(double amount) { this.critHitRate = Math.min(1.0, this.critHitRate + amount); }

    public double getCritMagnification() { return critMagnification; }
    public void setCritMagnification(double critMagnification) { this.critMagnification = critMagnification; }

    public double getBonusCritMagnification() { return bonusCritMagnification; }
    public void setBonusCritMagnification(double bonusCritMagnification) { this.bonusCritMagnification = bonusCritMagnification; }
    public void addBonusCritMagnification(double amount) { this.bonusCritMagnification += amount; }

    public long getBonusCritMagEndTime() { return bonusCritMagEndTime; }
    public void setBonusCritMagEndTime(long bonusCritMagEndTime) { this.bonusCritMagEndTime = bonusCritMagEndTime; }

    /**
     * 获取当前有效的总暴击倍率
     */
    public double getEffectiveCritMagnification() {
        double bonus = 0;
        if (bonusCritMagEndTime > System.currentTimeMillis()) {
            bonus = bonusCritMagnification;
        } else if (bonusCritMagnification > 0 && bonusCritMagEndTime > 0) {
            // 过期了，清除
            bonusCritMagnification = 0;
            bonusCritMagEndTime = 0;
        }
        return critMagnification + bonus;
    }

    /**
     * 获取额外暴击倍率剩余时间（秒）
     */
    public int getCritMagRemainingSeconds() {
        if (bonusCritMagEndTime <= 0) return 0;
        long remaining = (bonusCritMagEndTime - System.currentTimeMillis()) / 1000;
        return (int) Math.max(0, remaining);
    }

    // ===== 药水效果 =====
    public Map<String, PotionEffectData> getActivePotionEffects() { return activePotionEffects; }

    public PotionEffectData getPotionEffect(String effectType) {
        return activePotionEffects.get(effectType.toLowerCase());
    }

    public void setPotionEffect(String effectType, int level, int durationSeconds) {
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        activePotionEffects.put(effectType.toLowerCase(), new PotionEffectData(level, endTime));
    }

    public void removePotionEffect(String effectType) {
        activePotionEffects.remove(effectType.toLowerCase());
    }

    public boolean hasActivePotionEffect(String effectType) {
        PotionEffectData data = activePotionEffects.get(effectType.toLowerCase());
        if (data == null) return false;
        if (data.endTime < System.currentTimeMillis()) {
            activePotionEffects.remove(effectType.toLowerCase());
            return false;
        }
        return true;
    }

    /**
     * 清理过期的药水效果
     */
    public void cleanExpiredEffects() {
        activePotionEffects.entrySet().removeIf(e -> e.getValue().endTime < System.currentTimeMillis());
    }

    // ===== 连锁体验卡（矿场世界内） =====
    public String getChainCardType() { return chainCardType; }
    public void setChainCardType(String type) { this.chainCardType = type; }
    public int getChainCardBlocks() { return chainCardBlocks; }
    public void setChainCardBlocks(int blocks) { this.chainCardBlocks = blocks; }
    public long getChainCardEndTime() { return chainCardEndTime; }
    public void setChainCardEndTime(long time) { this.chainCardEndTime = time; }
    public int getChainCardHeight() { return chainCardHeight; }
    public void setChainCardHeight(int h) { this.chainCardHeight = Math.max(1, Math.min(15, h)); }
    public int getRayChainMaxBlocks() { return rayChainMaxBlocks; }
    public void setRayChainMaxBlocks(int max) { this.rayChainMaxBlocks = Math.min(15, max); }

    public boolean hasChainCard() {
        if (chainCardType == null) return false;
        if (chainCardEndTime < System.currentTimeMillis()) {
            chainCardType = null;
            chainCardBlocks = 0;
            chainCardHeight = 1;
            chainCardEndTime = 0;
            rayChainMaxBlocks = 0;
            return false;
        }
        return true;
    }

    public int getChainCardRemainingSeconds() {
        return (int) Math.max(0, (chainCardEndTime - System.currentTimeMillis()) / 1000);
    }

    // ===== 一般连锁（矿场世界外全地图） =====
    public long getGlobalChainEndTime() { return globalChainEndTime; }
    public void setGlobalChainEndTime(long time) { this.globalChainEndTime = time; }

    public boolean hasGlobalChain() {
        if (globalChainEndTime < System.currentTimeMillis()) {
            globalChainEndTime = 0;
            return false;
        }
        return globalChainEndTime > 0;
    }

    public int getGlobalChainRemainingSeconds() {
        return (int) Math.max(0, (globalChainEndTime - System.currentTimeMillis()) / 1000);
    }

    /**
     * 获取升级到下一级所需的总经验（等级² × 3）
     */
    public int getExpToNextLevel() {
        return level * level * 3;
    }

    /**
     * 检查是否可以升级
     */
    public boolean canLevelUp() {
        return exp >= getExpToNextLevel();
    }

    /**
     * 执行升级，返回是否成功
     */
    public boolean levelUp() {
        if (!canLevelUp()) return false;
        exp -= getExpToNextLevel();
        level++;
        levelsInCurrentTier++;
        return true;
    }

    /**
     * 获取从当前等级升到目标等级所需的总经验
     */
    public int getExpToTargetLevel(int targetLevel) {
        if (targetLevel <= level) return 0;
        int total = 0;
        for (int lv = level; lv < targetLevel; lv++) {
            total += lv * lv * 3;
        }
        return total;
    }

    /**
     * 重置所有进度（退出小队时），保留金币
     */
    public void resetProgress() {
        this.level = 1;
        this.exp = 0;
        this.totalExp = 0;
        this.pickaxeTier = PickaxeTier.WOOD;
        this.efficiencyLevel = 0;
        this.fortuneLevel = 0;
        this.unbreakingLevel = 0;
        this.levelsInCurrentTier = 0;
        this.critHitRate = 0.005;
        this.critMagnification = 0.5;
        this.bonusCritMagnification = 0;
        this.bonusCritMagEndTime = 0;
        this.activePotionEffects.clear();
        this.chainCardType = null;
        this.chainCardBlocks = 0;
        this.chainCardHeight = 1;
        this.chainCardEndTime = 0;
        this.rayChainMaxBlocks = 0;
        this.globalChainEndTime = 0;
    }

    /**
     * 重置等级和经验（退出小队时）
     */
    public void resetLevelAndExp() {
        resetProgress();
    }

    /**
     * 应用团队最高等级和经验
     */
    public void applyTeamMax(PlayerData maxData) {
        if (maxData.getLevel() > this.level) {
            this.level = maxData.getLevel();
            this.exp = maxData.getExp();
            this.totalExp = maxData.getTotalExp();
            this.efficiencyLevel = maxData.getEfficiencyLevel();
            this.fortuneLevel = maxData.getFortuneLevel();
            this.unbreakingLevel = maxData.getUnbreakingLevel();
            this.pickaxeTier = maxData.getPickaxeTier();
        }
    }

    /**
     * 药水效果数据内部类
     */
    public static class PotionEffectData {
        public final int level;
        public final long endTime;

        public PotionEffectData(int level, long endTime) {
            this.level = level;
            this.endTime = endTime;
        }

        public boolean isActive() {
            return endTime > System.currentTimeMillis();
        }

        public int getRemainingSeconds() {
            return (int) Math.max(0, (endTime - System.currentTimeMillis()) / 1000);
        }
    }
}
