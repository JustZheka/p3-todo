package com.example.demo.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("spring.ldap")
@Data
public class LdapProperties {
    String urls;
    String base;
    String username;
    String password;
}

