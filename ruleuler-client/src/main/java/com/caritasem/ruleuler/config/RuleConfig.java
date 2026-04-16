package com.caritasem.ruleuler.config;

import com.caritasem.ruleuler.grayscale.GrayscaleMetricsReporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Desc
 *
 * @author zengxzh@yonyou.com
 * @version V1.0.0
 * @date 2018/1/4
 */
@Configuration
@ImportResource({"classpath:urule-core-context.xml"})
public class RuleConfig {
    @Bean
    public PropertySourcesPlaceholderConfigurer propertySourceLoader() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        configurer.setOrder(1);
        return configurer;
    }

    @Bean
    @ConditionalOnProperty(name = "urule.resporityServerUrl")
    public GrayscaleMetricsReporter grayscaleMetricsReporter(
            @Value("${urule.resporityServerUrl}") String serverUrl) {
        return new GrayscaleMetricsReporter(serverUrl);
    }
}
