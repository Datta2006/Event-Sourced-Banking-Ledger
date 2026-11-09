package com.bank.accountcmd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Routes logical account/event-store SQL to one of three regional PostgreSQL shards.
 *
 * <p>This is the horizontal-fragmentation layer described in v2 §7. Application code
 * only ever names the logical tables ({@code ledger_events}, {@code account_core},
 * {@code account_pii}); the router chooses the physical database from the account's
 * registration-IP region. The region is frozen at account-open time, so an account
 * never migrates between shards.</p>
 */
@Component
public class ShardRouter {

    private static final Logger log = LoggerFactory.getLogger(ShardRouter.class);
    public static final List<String> REGIONS = List.of("APAC", "EU", "AMER");

    private final Map<String, HikariDataSource> dataSources = new LinkedHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new LinkedHashMap<>();
    private final Map<String, TransactionTemplate> transactionTemplates = new LinkedHashMap<>();
    private final JdbcTemplate coordinator;
    private final String dbUser;
    private final String dbPassword;

    public ShardRouter(
            JdbcTemplate coordinator,
            @Value("${app.shards.APAC}") String apacUrl,
            @Value("${app.shards.EU}") String euUrl,
            @Value("${app.shards.AMER}") String amerUrl,
            @Value("${app.db.username:ddbs}") String dbUser,
            @Value("${app.db.password:ddbs}") String dbPassword) {
        this.coordinator = coordinator;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        urls.put("APAC", apacUrl);
        urls.put("EU", euUrl);
        urls.put("AMER", amerUrl);
        for (String region : REGIONS) {
            String url = urls.get(region);
            ensureDatabase(url);
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(dbUser);
            cfg.setPassword(dbPassword);
            cfg.setMaximumPoolSize(8);
            cfg.setPoolName("shard-" + region.toLowerCase());
            cfg.setConnectionTimeout(8_000);
            HikariDataSource ds = new HikariDataSource(cfg);
            HikariDataSource old = dataSources.put(region, ds);
            if (old != null) {
                old.close();
            }
            JdbcTemplate template = new JdbcTemplate(ds);
            jdbcTemplates.put(region, template);
            transactionTemplates.put(region,
                    new TransactionTemplate(new DataSourceTransactionManager(ds)));
        }
    }

    public JdbcTemplate jdbc(String region) {
        JdbcTemplate template = jdbcTemplates.get(normalize(region));
        if (template == null) {
            throw new IllegalArgumentException("Unknown region: " + region);
        }
        return template;
    }

    public TransactionTemplate tx(String region) {
        TransactionTemplate template = transactionTemplates.get(normalize(region));
        if (template == null) {
            throw new IllegalArgumentException("Unknown region: " + region);
        }
        return template;
    }

    public JdbcTemplate coordinator() {
        return coordinator;
    }

    /** Find an account's shard, repairing the directory from the shards when needed. */
    public Optional<String> regionFor(UUID accountId) {
        List<String> rows = coordinator.query(
                "SELECT region FROM account_command.account_directory WHERE account_id = ?",
                (rs, n) -> rs.getString(1), accountId);
        if (!rows.isEmpty()) {
            return Optional.of(rows.get(0));
        }
        for (String region : REGIONS) {
            try {
                List<String> found = jdbc(region).query(
                        "SELECT region FROM account_core WHERE account_id = ?",
                        (rs, n) -> rs.getString(1), accountId);
                if (!found.isEmpty()) {
                    register(accountId, found.get(0));
                    return Optional.of(found.get(0));
                }
            } catch (RuntimeException e) {
                log.debug("Shard {} not ready while locating {}: {}", region, accountId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    public void register(UUID accountId, String region) {
        coordinator.update("""
                INSERT INTO account_command.account_directory(account_id, region, created_at)
                VALUES (?, ?, now())
                ON CONFLICT (account_id) DO NOTHING
                """, accountId, normalize(region));
    }

    public String regionOf(UUID accountId) {
        return regionFor(accountId)
                .orElseThrow(() -> new com.bank.accountcmd.domain.AccountExceptions.NotFound(
                        "Account not found: " + accountId));
    }

    /** Row counts for the System Ops shard map. */
    public List<Map<String, Object>> stats() {
        return REGIONS.stream().map(region -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shardId", "ds_" + region.toLowerCase());
            row.put("region", region);
            row.put("host", hostOf(region));
            row.put("status", "UP");
            Map<String, Object> counts = new LinkedHashMap<>();
            for (String table : List.of("ledger_events", "account_core", "account_pii")) {
                try {
                    Long count = jdbc(region).queryForObject("SELECT count(*) FROM " + table, Long.class);
                    counts.put(table, count == null ? 0 : count);
                } catch (RuntimeException e) {
                    counts.put(table, 0);
                    row.put("status", "DEGRADED");
                }
            }
            row.put("rowCounts", counts);
            return row;
        }).toList();
    }

    private String hostOf(String region) {
        try {
            URI uri = URI.create(configuredUrl(region).substring("jdbc:".length()));
            return uri.getHost() + ":" + (uri.getPort() == -1 ? 5432 : uri.getPort());
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private final Map<String, String> urls = new LinkedHashMap<>();

    private String configuredUrl(String region) {
        return urls.getOrDefault(region, "postgresql://localhost:5433/ddbs_" + region.toLowerCase());
    }

    public static String normalize(String region) {
        return region == null ? "APAC" : region.trim().toUpperCase();
    }

    private static String databaseName(String jdbcUrl) {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String path = uri.getPath() == null ? "" : uri.getPath();
        String name = path.startsWith("/") ? path.substring(1) : path;
        int q = name.indexOf('?');
        return q >= 0 ? name.substring(0, q) : name;
    }

    private void ensureDatabase(String jdbcUrl) {
        String db = databaseName(jdbcUrl);
        if (db.isBlank() || "postgres".equals(db) || "ledger".equals(db)) {
            return;
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection connection = DriverManager.getConnection(adminUrl, dbUser, dbPassword)) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, db);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE \"" + db.replace("\"", "") + "\"");
                log.info("Created regional shard database {}", db);
            }
        } catch (SQLException e) {
            log.warn("Could not ensure shard database {} ({}); will use existing connection if present",
                    db, e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        dataSources.values().forEach(HikariDataSource::close);
    }
}
