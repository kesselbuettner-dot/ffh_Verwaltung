package de.bierverein.api;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="members")
public class Member {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String name; private String email; private String phone; private String address;
 @Column(nullable=false) private boolean active=true;
 @Column(nullable=false, precision=12, scale=2) private BigDecimal balance=BigDecimal.ZERO;
 @OneToOne(mappedBy="member", fetch=FetchType.LAZY) private AppUser user;
 public Member(){} public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
 public String getAddress(){return address;} public void setAddress(String v){address=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
 public BigDecimal getBalance(){return balance;} public void setBalance(BigDecimal v){balance=v==null?BigDecimal.ZERO:v;} public AppUser getUser(){return user;} public void setUser(AppUser v){user=v;}
}
