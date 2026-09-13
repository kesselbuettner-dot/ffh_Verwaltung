package de.ffh_verwaltung.api;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="members")
public class Member {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String email;
    private boolean active=true;
    @Column(nullable=false) private BigDecimal balance=BigDecimal.ZERO;
    public Member(){}
    public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public BigDecimal getBalance(){return balance;} public void setBalance(BigDecimal v){balance=v;}
}
