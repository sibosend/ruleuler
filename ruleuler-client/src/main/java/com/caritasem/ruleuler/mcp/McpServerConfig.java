package com.caritasem.ruleuler.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ruleuler.mcp.enabled", havingValue = "true")
public class McpServerConfig {
}
