package com.example.demodrug.config;

import com.example.demodrug.security.TokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.annotation.Resource;

@Configuration
public class SecurityConfig {

    @Resource
    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                            boolean loggedIn = authentication != null
                                    && authentication.isAuthenticated()
                                    && !"anonymousUser".equals(authentication.getPrincipal());
                            response.setStatus(loggedIn ? 403 : 401);
                        })
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/login", "/health/db", "/actuator/health").permitAll()
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers("/audit/**").hasRole("ADMIN")
                        .requestMatchers("/catalog/**", "/inventory/**", "/reports/**").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers("/alerts/**", "/recommend/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/split/create").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.GET, "/split/*/label").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.POST, "/add").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.GET, "/prescriptions").hasAnyRole("ADMIN", "PHARMACIST", "NURSE")
                        .requestMatchers(HttpMethod.POST, "/dispense", "/return").hasAnyRole("ADMIN", "PHARMACIST", "NURSE")
                        .requestMatchers(HttpMethod.POST, "/device/scan/verify").authenticated()
                        .requestMatchers(HttpMethod.GET, "/list", "/search", "/records/**", "/nearExpiry", "/dashboard/summary", "/stock/status").authenticated()
                        .requestMatchers("/auth/me", "/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("请使用 /auth/login 登录");
        };
    }
}
