package com.example.user_module.common.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private Resource privateKey;
    private Resource publicKey;
    private String issuer;
    private String audience;
    private long accessExpirationTime;
    private long refreshExpirationTime;
}
