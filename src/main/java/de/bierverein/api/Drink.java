package de.bierverein.api;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="drinks")
public class Drink {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false) private String name;
    private String category;
    @Column(nullable=false, precision=10, scale=2) private BigDecimal price;
    @Column(length=20, unique=true) private String ean;
    @Column(nullable=false) private int stock = 0;
    @Column(nullable=false) private int warningThreshold = 0;
    private boolean active = true;

    public Drink() {}
    public Long getId(){return id;}
    public String getName(){return name;}
    public void setName(String v){name=v;}
    public String getCategory(){return category;}
    public void setCategory(String v){category=v;}
    public BigDecimal getPrice(){return price;}
    public void setPrice(BigDecimal v){price=v;}
    public String getEan(){return ean;}
    public void setEan(String v){ean=v;}
    public int getStock(){return stock;}
    public void setStock(int v){stock=v;}
    public int getWarningThreshold(){return warningThreshold;}
    public void setWarningThreshold(int v){warningThreshold=v;}
    public boolean isActive(){return active;}
    public void setActive(boolean v){active=v;}
}
