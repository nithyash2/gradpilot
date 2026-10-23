package com.gradpilot.outlook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OutlookProperties.class)
public class OutlookConfig {
}
