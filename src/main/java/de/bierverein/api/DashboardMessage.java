package de.bierverein.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dashboard_messages")
public class DashboardMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=20) private String type = "MESSAGE";
    @Column(nullable=false, length=140) private String title;
    @Column(length=2000) private String body;
    private Instant eventAt;
    private Instant publishFrom;
    private Instant publishUntil;
    @Column(nullable=false) private int priority = 0;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private boolean onWallboard = false;
    @Column(nullable=false, length=100) private String createdBy;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    public Long getId(){return id;} public String getType(){return type;} public void setType(String v){type=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getBody(){return body;} public void setBody(String v){body=v;}
    public Instant getEventAt(){return eventAt;} public void setEventAt(Instant v){eventAt=v;} public Instant getPublishFrom(){return publishFrom;} public void setPublishFrom(Instant v){publishFrom=v;}
    public Instant getPublishUntil(){return publishUntil;} public void setPublishUntil(Instant v){publishUntil=v;} public int getPriority(){return priority;} public void setPriority(int v){priority=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public boolean isOnWallboard(){return onWallboard;} public void setOnWallboard(boolean v){onWallboard=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public Instant getCreatedAt(){return createdAt;}
}
