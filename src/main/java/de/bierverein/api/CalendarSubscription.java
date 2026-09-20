package de.bierverein.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "calendar_subscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "token")
})
public class CalendarSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String username;
    @Column(nullable = false, length = 64)
    private String token;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getToken() { return token; }
    public void setToken(String value) { token = value; }
    public Instant getCreatedAt() { return createdAt; }
}
