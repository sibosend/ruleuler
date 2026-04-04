package com.caritasem.ruleuler.server.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class RealtimeMonitoringControllerTest {

    @Mock
    private DataSource clickHouseDs;

    @Mock(name = "alertConfigService")
    private AlertConfigService alertConfigService;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData resultSetMetaData;

    @InjectMocks
    private RealtimeMonitoringController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws SQLException {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        
        lenient().when(clickHouseDs.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        lenient().when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        
        AlertConfig mockConfig = new AlertConfig();
        lenient().when(alertConfigService.getCachedConfig()).thenReturn(mockConfig);
    }

    @Test
    void dashboard_returnsValidAggregation() throws Exception {
        // mock metadata
        when(resultSetMetaData.getColumnCount()).thenReturn(4);
        when(resultSetMetaData.getColumnLabel(1)).thenReturn("day_type");
        when(resultSetMetaData.getColumnLabel(2)).thenReturn("total_executions");
        when(resultSetMetaData.getColumnLabel(3)).thenReturn("error_executions");
        when(resultSetMetaData.getColumnLabel(4)).thenReturn("missing_executions");

        // mock two rows, one for today and one for yesterday
        when(resultSet.next()).thenReturn(true, true, false);
        
        when(resultSet.getObject(1)).thenReturn("today", "yesterday");
        when(resultSet.getObject(2)).thenReturn(100L, 200L); // total
        when(resultSet.getObject(3)).thenReturn(5L, 8L);   // errors
        when(resultSet.getObject(4)).thenReturn(10L, 14L);  // missing

        mockMvc.perform(get("/api/monitoring/realtime/dashboard")
                .param("project", "test_proj")
                .param("packageId", "test_pkg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.today.total_executions").value(100))
            .andExpect(jsonPath("$.data.today.error_rate").value(0.05))
            .andExpect(jsonPath("$.data.today.anomaly_rate").value(0.1))
            .andExpect(jsonPath("$.data.yesterday.total_executions").value(200))
            .andExpect(jsonPath("$.data.yesterday.error_rate").value(0.04))
            .andExpect(jsonPath("$.data.yesterday.anomaly_rate").value(0.07));

        verify(clickHouseDs, times(1)).getConnection();
    }

    @Test
    void variables_handlesSorting_andAlertFlags() throws Exception {
        when(resultSetMetaData.getColumnCount()).thenReturn(9);
        when(resultSetMetaData.getColumnLabel(1)).thenReturn("var_category");
        when(resultSetMetaData.getColumnLabel(2)).thenReturn("var_name");
        when(resultSetMetaData.getColumnLabel(3)).thenReturn("var_type");
        when(resultSetMetaData.getColumnLabel(4)).thenReturn("sample_count");
        when(resultSetMetaData.getColumnLabel(5)).thenReturn("missing_count");
        when(resultSetMetaData.getColumnLabel(6)).thenReturn("sum_val_num");
        when(resultSetMetaData.getColumnLabel(7)).thenReturn("error_count");
        when(resultSetMetaData.getColumnLabel(8)).thenReturn("min_val_num");
        when(resultSetMetaData.getColumnLabel(9)).thenReturn("max_val_num");

        when(resultSet.next()).thenReturn(true, false);
        
        when(resultSet.getObject(1)).thenReturn("TestCat");
        when(resultSet.getObject(2)).thenReturn("testVar");
        when(resultSet.getObject(3)).thenReturn("Integer");
        when(resultSet.getObject(4)).thenReturn(50L);  // samples
        when(resultSet.getObject(5)).thenReturn(5L);   // missing (missing_rate = 0.1) -> should alert as it's > default 0.05
        when(resultSet.getObject(6)).thenReturn(450.0); // sum for 45 numeric samples -> mean 10.0
        when(resultSet.getObject(7)).thenReturn(0L);   // errors
        when(resultSet.getObject(8)).thenReturn(1.0);  // min
        when(resultSet.getObject(9)).thenReturn(20.0); // max

        mockMvc.perform(get("/api/monitoring/realtime/variables")
                .param("project", "test_proj")
                .param("packageId", "test_pkg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].var_category").value("TestCat"))
            .andExpect(jsonPath("$.data[0].missing_rate").value(0.1))
            .andExpect(jsonPath("$.data[0].mean").value(10.0))
            .andExpect(jsonPath("$.data[0].alert_flags").value("missing_rate"));
    }

    @Test
    void missingRateTrend_calculatesSpike() throws Exception {
        when(resultSetMetaData.getColumnCount()).thenReturn(3);
        when(resultSetMetaData.getColumnLabel(1)).thenReturn("window_start");
        when(resultSetMetaData.getColumnLabel(2)).thenReturn("sample_count");
        when(resultSetMetaData.getColumnLabel(3)).thenReturn("missing_count");

        // Two rows to show spike -> difference between 0.05 and 0.45 is 0.4 > 0.1
        when(resultSet.next()).thenReturn(true, true, false);

        when(resultSet.getObject(1)).thenReturn("2023-01-01 10:00:00", "2023-01-01 10:05:00");
        when(resultSet.getObject(2)).thenReturn(100L, 100L); // samples
        when(resultSet.getObject(3)).thenReturn(5L, 45L);   // => rate 0.05 then 0.45

        mockMvc.perform(get("/api/monitoring/realtime/missing-rate-trend")
                .param("project", "test_proj")
                .param("packageId", "test_pkg")
                .param("varCategory", "FlightInfo")
                .param("varName", "aircraft_type"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].missing_rate").value(0.05))
            .andExpect(jsonPath("$.data[0].spike").value(false))
            .andExpect(jsonPath("$.data[1].missing_rate").value(0.45))
            .andExpect(jsonPath("$.data[1].spike").value(true)); // Spike should be triggered
    }
}
