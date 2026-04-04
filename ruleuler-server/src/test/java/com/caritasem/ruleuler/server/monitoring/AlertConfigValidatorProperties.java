package com.caritasem.ruleuler.server.monitoring;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 告警配置校验属性测试
 * Feature: monitoring-realtime-enhancement, Property 9: 告警配置校验
 */
class AlertConfigValidatorProperties {

    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 9: 拒绝负数值和大于 1.0 的比率")
    void rejectInvalidValues(@ForAll("invalidConfig") AlertConfig config) {
        AlertConfigService service = new AlertConfigService();
        assertThatIllegalArgumentException().isThrownBy(() -> service.validate(config));
    }

    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 9: 接受合法值")
    void allowValidValues(@ForAll("validConfig") AlertConfig config) {
        AlertConfigService service = new AlertConfigService();
        assertThatNoException().isThrownBy(() -> service.validate(config));
    }

    @Provide
    Arbitrary<AlertConfig> validConfig() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 1.0), // missingRateMax
                Arbitraries.doubles().between(0.0, 1.0), // missingRateSpikeDelta
                Arbitraries.doubles().between(0.0, 1.0), // outlierRateMax
                Arbitraries.doubles().greaterOrEqual(0.0), // skewnessAbsMax
                Arbitraries.doubles().between(0.0, 0.5), // psiWarning
                Arbitraries.doubles().between(0.51, 1.0), // psiAlert
                Arbitraries.doubles().between(0.0, 1.0)  // enumDriftThreshold
        ).as((missingRateMax, missingRateSpikeDelta, outlierRateMax, skewnessAbsMax, psiWarning, psiAlert, enumDriftThreshold) -> {
            AlertConfig config = new AlertConfig();
            config.setMissingRateMax(missingRateMax);
            config.setMissingRateSpikeDelta(missingRateSpikeDelta);
            config.setOutlierRateMax(outlierRateMax);
            config.setSkewnessAbsMax(skewnessAbsMax);
            config.setPsiWarning(psiWarning);
            config.setPsiAlert(psiAlert);
            config.setEnumDriftThreshold(enumDriftThreshold);
            return config;
        });
    }

    @Provide
    Arbitrary<AlertConfig> invalidConfig() {
        // Any config with one of the constraints violated
        return Combinators.combine(
                Arbitraries.doubles().lessThan(0.0), // invalid missingRateMax
                validConfig()
        ).as((invalidRate, baseConfig) -> {
            baseConfig.setMissingRateMax(invalidRate);
            return baseConfig;
        });
    }
}
