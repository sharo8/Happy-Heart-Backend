package com.happyhearts.config;

import com.happyhearts.security.ClientInfoMdcFilter;
import com.happyhearts.security.GmpWriteBlockingFilter;
import com.happyhearts.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ClientInfoMdcFilter clientInfoMdcFilter;
    private final GmpWriteBlockingFilter gmpWriteBlockingFilter;
    private final RfidApiKeyFilter rfidApiKeyFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/first-login/setup-password",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/refresh",
                                "/api/v1/public/access-requests"
                        ).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/attendance/scan",
                                "/api/v1/attendance/sync-offline",
                                "/api/v1/attendance/unknown-scan",
                                "/api/attendance/scan",
                                "/api/attendance/sync-offline",
                                "/api/attendance/sync-offline-batch",
                                "/api/attendance/heartbeat")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/devices/*/heartbeat")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/attendance/live",
                                "/api/v1/attendance/today",
                                "/api/v1/attendance/today/latest",
                                "/api/v1/attendance/recent",
                                "/api/v1/attendance/unknown-cards",
                                "/api/v1/attendance/report",
                                "/api/v1/attendance/reports/analytics",
                                "/api/v1/attendance/employee/*/detail",
                                "/api/v1/attendance/employee/*/summary",
                                "/api/v1/devices")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/attendance/reader/scan",
                                "/api/v1/attendance/reader/sync")
                        .hasAuthority("RFID_READER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rfid-readers/*/heartbeat")
                        .hasAuthority("RFID_READER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rfidApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(clientInfoMdcFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(gmpWriteBlockingFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins().isEmpty()
                ? List.of("http://localhost:3000")
                : corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
