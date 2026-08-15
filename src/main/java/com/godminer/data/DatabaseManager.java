package com.godminer.data;

import com.godminer.GoldMiner;
import com.godminer.model.PlayerData;
import com.godminer.model.PickaxeTier;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库管理器 (SQLite / MySQL)
 */
public class DatabaseManager {

    private final GoldMiner plugin;
    private HikariDataSource dataSource;
    private String type;

    public DatabaseManager(GoldMiner plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        this.type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();

        if (type.equals("mysql")) {
            return initMySQL();
        } else {
            return initSQLite();
        }
    }

    private boolean initSQLite() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, "data.db");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(2);
            config.setConnectionTimeout(5000);
            this.dataSource = new HikariDataSource(config);

            createTables();
            plugin.getLogger().info("SQLite 数据库连接成功！");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("SQLite 数据库连接失败: " + e.getMessage());
            return false;
        }
    }

    private boolean initMySQL() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "goldminer");
            String username = plugin.getConfig().getString("storage.mysql.username", "root");
            String password = plugin.getConfig().getString("storage.mysql.password", "password");
            boolean useSSL = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);
            int poolSize = plugin.getConfig().getInt("storage.mysql.pool-size", 10);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
            config.setConnectionTimeout(10000);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            this.dataSource = new HikariDataSource(config);

            createTables();
            plugin.getLogger().info("MySQL 数据库连接成功！");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("MySQL 数据库连接失败: " + e.getMessage());
            plugin.getLogger().warning("回退到 SQLite...");
            return initSQLite();
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "coins INT DEFAULT 0, " +
                "exp INT DEFAULT 0, " +
                "level INT DEFAULT 1, " +
                "total_exp INT DEFAULT 0, " +
                "pickaxe_tier VARCHAR(20) DEFAULT 'WOOD', " +
                "efficiency_level INT DEFAULT 0, " +
                "fortune_level INT DEFAULT 0, " +
                "unbreaking_level INT DEFAULT 0, " +
                "team_name VARCHAR(64), " +
                "last_save BIGINT DEFAULT 0, " +
                "crit_hit_rate DOUBLE DEFAULT 0.005, " +
                "crit_magnification DOUBLE DEFAULT 0.5, " +
                "bonus_crit_mag DOUBLE DEFAULT 0, " +
                "bonus_crit_mag_end BIGINT DEFAULT 0, " +
                "potion_effects TEXT DEFAULT '', " +
                "chain_card_type VARCHAR(20) DEFAULT '', " +
                "chain_card_blocks INT DEFAULT 0, " +
                "chain_card_end BIGINT DEFAULT 0, " +
                "chain_card_height INT DEFAULT 1, " +
                "ray_chain_max INT DEFAULT 0, " +
                "global_chain_end BIGINT DEFAULT 0" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据表失败: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void savePlayer(PlayerData data) {
        String sql = "INSERT OR REPLACE INTO players (uuid, player_name, coins, exp, level, total_exp, " +
                "pickaxe_tier, efficiency_level, fortune_level, unbreaking_level, team_name, last_save, " +
                "crit_hit_rate, crit_magnification, bonus_crit_mag, bonus_crit_mag_end, " +
                "potion_effects, chain_card_type, chain_card_blocks, chain_card_end, chain_card_height, ray_chain_max, global_chain_end) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getPlayerName());
            ps.setInt(3, data.getCoins());
            ps.setInt(4, data.getExp());
            ps.setInt(5, data.getLevel());
            ps.setInt(6, data.getTotalExp());
            ps.setString(7, data.getPickaxeTier().name());
            ps.setInt(8, data.getEfficiencyLevel());
            ps.setInt(9, data.getFortuneLevel());
            ps.setInt(10, data.getUnbreakingLevel());
            ps.setString(11, data.getTeamName());
            ps.setLong(12, System.currentTimeMillis());
            ps.setDouble(13, data.getCritHitRate());
            ps.setDouble(14, data.getCritMagnification());
            ps.setDouble(15, data.getBonusCritMagnification());
            ps.setLong(16, data.getBonusCritMagEndTime());
            ps.setString(17, serializePotionEffects(data));
            ps.setString(18, data.getChainCardType() != null ? data.getChainCardType() : "");
            ps.setInt(19, data.getChainCardBlocks());
            ps.setLong(20, data.getChainCardEndTime());
            ps.setInt(21, data.getChainCardHeight());
            ps.setInt(22, data.getRayChainMaxBlocks());
            ps.setLong(23, data.getGlobalChainEndTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("保存玩家数据失败: " + e.getMessage());
        }
    }

    private String serializePotionEffects(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        for (var entry : data.getActivePotionEffects().entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey()).append(":").append(entry.getValue().level)
                    .append(":").append(entry.getValue().endTime);
        }
        return sb.toString();
    }

    private void deserializePotionEffects(PlayerData data, String serialized) {
        if (serialized == null || serialized.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String part : serialized.split(";")) {
            String[] parts = part.split(":");
            if (parts.length >= 3) {
                try {
                    String type = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    long endTime = Long.parseLong(parts[2]);
                    if (endTime > now) {
                        data.getActivePotionEffects().put(type,
                                new PlayerData.PotionEffectData(level, endTime));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public PlayerData loadPlayer(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerData data = new PlayerData(uuid);
                data.setPlayerName(rs.getString("player_name"));
                data.setCoins(rs.getInt("coins"));
                data.setExp(rs.getInt("exp"));
                data.setLevel(rs.getInt("level"));
                data.setTotalExp(rs.getInt("total_exp"));
                data.setPickaxeTier(PickaxeTier.valueOf(rs.getString("pickaxe_tier")));
                data.setEfficiencyLevel(rs.getInt("efficiency_level"));
                data.setFortuneLevel(rs.getInt("fortune_level"));
                data.setUnbreakingLevel(rs.getInt("unbreaking_level"));
                data.setTeamName(rs.getString("team_name"));
                data.setLastSaveTime(rs.getLong("last_save"));
                // 新字段
                data.setCritHitRate(rs.getDouble("crit_hit_rate"));
                data.setCritMagnification(rs.getDouble("crit_magnification"));
                data.setBonusCritMagnification(rs.getDouble("bonus_crit_mag"));
                data.setBonusCritMagEndTime(rs.getLong("bonus_crit_mag_end"));
                deserializePotionEffects(data, rs.getString("potion_effects"));
                String cardType = rs.getString("chain_card_type");
                data.setChainCardType(cardType != null && !cardType.isEmpty() ? cardType : null);
                data.setChainCardBlocks(rs.getInt("chain_card_blocks"));
                data.setChainCardEndTime(rs.getLong("chain_card_end"));
                data.setChainCardHeight(rs.getInt("chain_card_height"));
                data.setRayChainMaxBlocks(rs.getInt("ray_chain_max"));
                data.setGlobalChainEndTime(rs.getLong("global_chain_end"));
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("加载玩家数据失败: " + e.getMessage());
        }
        return null;
    }

    public Map<UUID, PlayerData> loadAllPlayers() {
        Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
        String sql = "SELECT * FROM players ORDER BY coins DESC, level DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                PlayerData data = new PlayerData(uuid);
                data.setPlayerName(rs.getString("player_name"));
                data.setCoins(rs.getInt("coins"));
                data.setExp(rs.getInt("exp"));
                data.setLevel(rs.getInt("level"));
                data.setTotalExp(rs.getInt("total_exp"));
                data.setPickaxeTier(PickaxeTier.valueOf(rs.getString("pickaxe_tier")));
                data.setEfficiencyLevel(rs.getInt("efficiency_level"));
                data.setFortuneLevel(rs.getInt("fortune_level"));
                data.setUnbreakingLevel(rs.getInt("unbreaking_level"));
                data.setTeamName(rs.getString("team_name"));
                data.setLastSaveTime(rs.getLong("last_save"));
                data.setCritHitRate(rs.getDouble("crit_hit_rate"));
                data.setCritMagnification(rs.getDouble("crit_magnification"));
                data.setBonusCritMagnification(rs.getDouble("bonus_crit_mag"));
                data.setBonusCritMagEndTime(rs.getLong("bonus_crit_mag_end"));
                deserializePotionEffects(data, rs.getString("potion_effects"));
                String cardType2 = rs.getString("chain_card_type");
                data.setChainCardType(cardType2 != null && !cardType2.isEmpty() ? cardType2 : null);
                data.setChainCardBlocks(rs.getInt("chain_card_blocks"));
                data.setChainCardEndTime(rs.getLong("chain_card_end"));
                data.setChainCardHeight(rs.getInt("chain_card_height"));
                data.setRayChainMaxBlocks(rs.getInt("ray_chain_max"));
                data.setGlobalChainEndTime(rs.getLong("global_chain_end"));
                players.put(uuid, data);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("加载所有玩家数据失败: " + e.getMessage());
        }
        return players;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
