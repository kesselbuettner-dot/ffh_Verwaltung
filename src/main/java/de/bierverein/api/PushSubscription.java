package de.bierverein.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="push_subscriptions", uniqueConstraints=@UniqueConstraint(columnNames="endpoint"))
public class PushSubscription {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=1200) private String endpoint;
 @Column(nullable=false,length=300) private String p256dh;
 @Column(nullable=false,length=200) private String auth;
 @Column(nullable=false,length=100) private String username;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 public Long getId(){return id;} public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
 public String getP256dh(){return p256dh;} public void setP256dh(String v){p256dh=v;} public String getAuth(){return auth;} public void setAuth(String v){auth=v;}
 public String getUsername(){return username;} public void setUsername(String v){username=v;} public Instant getCreatedAt(){return createdAt;}
}
