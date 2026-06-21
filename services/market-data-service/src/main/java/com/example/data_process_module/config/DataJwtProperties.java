package com.example.data_process_module.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class DataJwtProperties {
    private Resource publicKey;
    private String issuer;
    private String audience;
}
