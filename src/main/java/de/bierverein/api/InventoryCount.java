package de.bierverein.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="inventory_counts")
public class InventoryCount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Drink drink;
    @Column(nullable=false) private int previousStock;
    @Column(nullable=false) private int countedStock;
    @Column(nullable=false) private int difference;
    @Column(nullable=false) private Instant countedAt;
    @Column(nullable=false) private String countedBy;

    public InventoryCount() {}
    public Long getId(){return id;}
    public Drink getDrink(){return drink;} public void setDrink(Drink v){drink=v;}
    public int getPreviousStock(){return previousStock;} public void setPreviousStock(int v){previousStock=v;}
    public int getCountedStock(){return countedStock;} public void setCountedStock(int v){countedStock=v;}
    public int getDifference(){return difference;} public void setDifference(int v){difference=v;}
    public Instant getCountedAt(){return countedAt;} public void setCountedAt(Instant v){countedAt=v;}
    public String getCountedBy(){return countedBy;} public void setCountedBy(String v){countedBy=v;}
}
