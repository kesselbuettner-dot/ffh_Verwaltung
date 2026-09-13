package de.ffh_verwaltung.api;
import jakarta.persistence.*;
@Entity
@Table(name="app_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true) private String username;
    @Column(nullable=false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
    @OneToOne private Member member;
    private boolean enabled=true;
    public AppUser(){}
    public Long getId(){return id;}
    public String getUsername(){return username;}
    public void setUsername(String v){username=v;}
    public String getPasswordHash(){return passwordHash;}
    public void setPasswordHash(String v){passwordHash=v;}
    public Role getRole(){return role;}
    public void setRole(Role v){role=v;}
    public Member getMember(){return member;}
    public void setMember(Member v){member=v;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
}
