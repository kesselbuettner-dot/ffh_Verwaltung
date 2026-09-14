package de.bierverein.api;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
 private final SecretKey key; private final long expirationMinutes;
 public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.expiration-minutes}") long exp){
   if(secret.length()<32) throw new IllegalArgumentException("JWT_SECRET muss mindestens 32 Zeichen lang sein");
   key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); expirationMinutes=exp;
 }
 public String create(AppUser user){
   Instant now=Instant.now();
   return Jwts.builder().subject(user.getUsername()).claim("role",user.getRole().name())
     .claim("userId",user.getId()).issuedAt(Date.from(now))
     .expiration(Date.from(now.plusSeconds(expirationMinutes*60))).signWith(key).compact();
 }
 public String username(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
 public SecretKey key(){return key;}
}
