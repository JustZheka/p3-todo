package com.example.demo.utils;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("jwt")
@Data
public class JwtProperties {
    private String secret;
    private int accessExpirationMinutes;
    private int refreshExpirationDays;
}
