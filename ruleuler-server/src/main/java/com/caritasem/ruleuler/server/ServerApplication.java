package com.caritasem.ruleuler.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServerApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ServerApplication.class, args);
		Environment env = context.getEnvironment();
		String[] activeProfiles = env.getActiveProfiles();
		
		if (activeProfiles.length > 0) {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			for (String profile : activeProfiles) {
				try {
					Resource[] resources = resolver.getResources("classpath*:application-" + profile + ".yml");
					if (resources.length == 0) {
						throw new IllegalStateException("Profile '" + profile + "' 已激活，但找不到对应的配置文件: application-" + profile + ".yml/yaml/properties");
					}
				} catch (Exception e) {
					throw new IllegalStateException("检查 profile 配置文件失败: " + e.getMessage(), e);
				}
			}
		}
	}
}
