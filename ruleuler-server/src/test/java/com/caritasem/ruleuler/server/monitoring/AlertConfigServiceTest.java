package com.caritasem.ruleuler.server.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlertConfigServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AlertConfigService alertConfigService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getCachedConfig_ReturnsFromDb_WhenNoCache() {
        AlertConfig mockConfig = new AlertConfig();
        mockConfig.setMissingRateMax(0.25);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(mockConfig));

        AlertConfig config = alertConfigService.getCachedConfig();

        assertThat(config.getMissingRateMax()).isEqualTo(0.25);
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class));
        
        // Second call should return cached without hitting DB
        AlertConfig config2 = alertConfigService.getCachedConfig();
        assertThat(config2.getMissingRateMax()).isEqualTo(0.25);
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class));
    }

    @Test
    void getCachedConfig_ReturnsFallback_WhenDbEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        AlertConfig config = alertConfigService.getCachedConfig();
        
        assertThat(config).isNotNull(); // should be a default new instance
        assertThat(config.getMissingRateMax()).isEqualTo(0.05); // default from AlertConfig.java
    }

    @Test
    void update_PerformsValidation_AndClearsCache() {
        AlertConfig mockConfig = new AlertConfig();
        mockConfig.setMissingRateMax(0.3);
        mockConfig.setMissingRateSpikeDelta(0.1);
        mockConfig.setOutlierRateMax(0.05);
        mockConfig.setSkewnessAbsMax(2.0);
        mockConfig.setPsiWarning(0.1);
        mockConfig.setPsiAlert(0.2);
        mockConfig.setEnumDriftThreshold(0.05);

        // Pre-populate cache
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(mockConfig));
        alertConfigService.getCachedConfig();
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class));

        // Update
        AlertConfig newConfig = new AlertConfig();
        newConfig.setMissingRateMax(0.4);
        newConfig.setMissingRateSpikeDelta(0.1);
        newConfig.setOutlierRateMax(0.05);
        newConfig.setSkewnessAbsMax(2.0);
        newConfig.setPsiWarning(0.1);
        newConfig.setPsiAlert(0.2);
        newConfig.setEnumDriftThreshold(0.05);

        alertConfigService.update(newConfig);

        verify(jdbcTemplate, times(1)).update(anyString(),
                eq(0.4), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // Cache should be hit again since it was invalidated
        alertConfigService.getCachedConfig();
        verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class));
    }

    @Test
    void validate_ThrowsIllegalArgument_WhenValuesInvalid() {
        AlertConfig newConfig = new AlertConfig();
        newConfig.setMissingRateMax(1.5); // Invalid, > 1.0

        assertThatThrownBy(() -> alertConfigService.update(newConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missingRateMax");
    }
}
