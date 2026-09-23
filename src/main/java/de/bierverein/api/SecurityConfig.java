package de.bierverein.api;

import java.util.List;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

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
                .requestMatchers(HttpMethod.GET, "/api/settings/logo", "/api/settings/imprint").permitAll()
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/manifest.json",
                    "/sw.js",
                    "/tabler-icons.js",
                    "/menu-designer.js",
                    "/menu-designer.css",
                    "/ui-theme.css",
                    "/design-system.css",
                    "/design-system.js",
                    "/page-templates.js",
                    "/device-cycle-tasks.js",
                    "/page-templates.css",
                    "/wehrleiter.js",
                    "/wehrleiter.css",
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
    JwtAuthenticationConverter jwtAuthenticationConverter(AppUserRepository users) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaim("userId");
            if (!(claim instanceof Number id)) {
                throw new InvalidBearerTokenException("Benutzerkennung im Token fehlt.");
            }
            AppUser user = users.findById(id.longValue())
                    .orElseThrow(() -> new InvalidBearerTokenException("Benutzerkonto existiert nicht."));
            // Resolve the current account and legacy role on EACH request.
            // Revocations, deactivation and role changes must take effect even
            // while an older, otherwise valid JWT is still held by the browser.
            if (!user.isEnabled() || !user.isRegistrationApproved() ||
                    user.getRole() == null) {
                throw new InvalidBearerTokenException("Benutzerkonto nicht freigeschaltet.");
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        });
        return converter;
    }
}
