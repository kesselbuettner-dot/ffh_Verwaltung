package de.ffh_verwaltung.api;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean JwtDecoder jwtDecoder(JwtService jwt){return NimbusJwtDecoder.withSecretKey(jwt.key()).build();}
 @Bean SecurityFilterChain security(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
   http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
     .authorizeHttpRequests(a->a
       .requestMatchers("/","/index.html","/api/auth/**","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll()
       .requestMatchers("/api/admin/**").hasRole("ADMIN")
       .requestMatchers("/api/theke/**").hasAnyRole("ADMIN","THEKE")
       .requestMatchers("/api/member/**").hasAnyRole("ADMIN","THEKE","MEMBER")
       .requestMatchers("/api/members/**","/api/drinks/**","/api/bookings/**").authenticated()
       .anyRequest().authenticated())
     .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)));
   return http.build();
 }
 @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){
   JwtAuthenticationConverter c=new JwtAuthenticationConverter();
   c.setJwtGrantedAuthoritiesConverter(jwt->{
     String role=jwt.getClaimAsString("role");
     return List.of(new SimpleGrantedAuthority("ROLE_"+role));
   }); return c;
 }
}
