package com.example.demo.security;

import com.example.demo.utils.JwtAuthFilter;
import com.example.demo.utils.LdapProperties;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@Profile("dev")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class DevSecurityConfig {

    JwtAuthFilter jwtAuthFilter;
    LdapProperties ldapProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http)
        throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        "/auth/login",
                        "/auth/logout",
                        "/auth/refresh",
                        "/public/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    )
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public LdapContextSource contextSource() {
        val contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrls());
        contextSource.setBase(ldapProperties.getBase());
        contextSource.setUserDn(ldapProperties.getUsername());
        contextSource.setPassword(ldapProperties.getPassword());
        return contextSource;
    }

    @Bean
    public AuthenticationManager authenticationManager(
        final BaseLdapPathContextSource contextSource
    ) {
        val groupSearchBase = StringUtils.hasText(
            ldapProperties.getGroupSearchBase()
        )
            ? ldapProperties.getGroupSearchBase()
            : "";
        val authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(
            contextSource,
            groupSearchBase
        );
        if (StringUtils.hasText(ldapProperties.getGroupSearchFilter())) {
            authoritiesPopulator.setGroupSearchFilter(
                ldapProperties.getGroupSearchFilter()
            );
        }

        val factory = new LdapBindAuthenticationManagerFactory(contextSource);
        factory.setUserDnPatterns("cn={0}");
        factory.setLdapAuthoritiesPopulator(authoritiesPopulator);
        return factory.createAuthenticationManager();
    }
}
