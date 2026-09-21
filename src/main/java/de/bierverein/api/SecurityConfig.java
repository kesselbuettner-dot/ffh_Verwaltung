package de.bierverein.api;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwt) {
        return NimbusJwtDecoder.withSecretKey(jwt.key())
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS384)
                .build();
    }

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            JwtAuthenticationConverter converter) throws Exception {

        http
            .csrf(c -> c.disable())
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/settings/logo").permitAll()
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/manifest.json",
                    "/sw.js",
                    "/favicon.ico",
                    "/icons/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/uploads/articles/**",
                    "/api/auth/**",
                    "/api/calendar/feed/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                .requestMatchers("/api/devices/**")
                    .authenticated()

                .requestMatchers("/api/theke/**")
                    .hasAnyRole("ADMIN", "THEKE", "MEMBER", "GETRAENKEWART")

                .requestMatchers("/api/member/**")
                    .hasAnyRole("ADMIN", "THEKE", "MEMBER")

                .anyRequest()
                    .authenticated()
            )
            .oauth2ResourceServer(o ->
                o.jwt(j ->
                    j.jwtAuthenticationConverter(converter)
                )
            );

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });

        return converter;
    }
}
