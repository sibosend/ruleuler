package com.caritasem.ruleuler.server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertConfigService {
    private static final Logger log = LoggerFactory.getLogger(AlertConfigService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private volatile AlertConfig cached;
    private volatile long lastRefreshMs;
    private static final long CACHE_TTL_MS = 60_000;

    public AlertConfig getCachedConfig() {
        if (cached == null || System.currentTimeMillis() - lastRefreshMs > CACHE_TTL_MS) {
            refresh();
        }
        return cached;
    }

    private synchronized void refresh() {
        if (cached != null && System.currentTimeMillis() - lastRefreshMs <= CACHE_TTL_MS) {
            return;
        }
        try {
            String sql = "SELECT * FROM ruleuler_monitoring_alert_config WHERE id = 1";
            List<AlertConfig> configs = jdbcTemplate.query(sql, (rs, rowNum) -> {
                AlertConfig config = new AlertConfig();
                config.setMissingRateMax(rs.getDouble("missing_rate_max"));
                config.setMissingRateSpikeDelta(rs.getDouble("missing_rate_spike_delta"));
                config.setOutlierRateMax(rs.getDouble("outlier_rate_max"));
                config.setSkewnessAbsMax(rs.getDouble("skewness_abs_max"));
                config.setPsiWarning(rs.getDouble("psi_warning"));
                config.setPsiAlert(rs.getDouble("psi_alert"));
                config.setEnumDriftThreshold(rs.getDouble("enum_drift_threshold"));
                return config;
            });
            if (!configs.isEmpty()) {
                cached = configs.get(0);
                lastRefreshMs = System.currentTimeMillis();
            } else {
                // Return default if not populated
                cached = new AlertConfig();
            }
        } catch (Exception e) {
            log.error("Failed to refresh AlertConfig from database. Using default/stale cache.", e);
            if (cached == null) {
                cached = new AlertConfig();
            }
        }
    }

    public void update(AlertConfig config) {
        validate(config);
        String sql = """
            UPDATE ruleuler_monitoring_alert_config
            SET missing_rate_max = ?, missing_rate_spike_delta = ?,
                outlier_rate_max = ?, skewness_abs_max = ?,
                psi_warning = ?, psi_alert = ?, enum_drift_threshold = ?
            WHERE id = 1
        """;
        jdbcTemplate.update(sql,
                config.getMissingRateMax(), config.getMissingRateSpikeDelta(),
                config.getOutlierRateMax(), config.getSkewnessAbsMax(),
                config.getPsiWarning(), config.getPsiAlert(), config.getEnumDriftThreshold());
        cached = null; // Invalidate cache
    }

    public void validate(AlertConfig config) {
        if (config.getMissingRateMax() < 0 || config.getMissingRateMax() > 1.0) {
            throw new IllegalArgumentException("missingRateMax must be between 0 and 1.0");
        }
        if (config.getMissingRateSpikeDelta() < 0 || config.getMissingRateSpikeDelta() > 1.0) {
            throw new IllegalArgumentException("missingRateSpikeDelta must be between 0 and 1.0");
        }
        if (config.getOutlierRateMax() < 0 || config.getOutlierRateMax() > 1.0) {
            throw new IllegalArgumentException("outlierRateMax must be between 0 and 1.0");
        }
        if (config.getSkewnessAbsMax() < 0) {
            throw new IllegalArgumentException("skewnessAbsMax must be non-negative");
        }
        if (config.getPsiWarning() < 0 || config.getPsiWarning() > 1.0) {
            throw new IllegalArgumentException("psiWarning must be between 0 and 1.0");
        }
        if (config.getPsiAlert() < 0 || config.getPsiAlert() > 1.0) {
            throw new IllegalArgumentException("psiAlert must be between 0 and 1.0");
        }
        if (config.getPsiWarning() >= config.getPsiAlert()) {
            throw new IllegalArgumentException("psiWarning must be less than psiAlert");
        }
        if (config.getEnumDriftThreshold() < 0 || config.getEnumDriftThreshold() > 1.0) {
            throw new IllegalArgumentException("enumDriftThreshold must be between 0 and 1.0");
        }
    }
}
