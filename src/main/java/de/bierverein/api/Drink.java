package de.ffh_verwaltung.api;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="drinks")
public class Drink {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String name; private String category;
 @Column(nullable=false) private BigDecimal price; private boolean active=true;
 public Drink(){} public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getCategory(){return category;} public void setCategory(String v){category=v;}
 public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
