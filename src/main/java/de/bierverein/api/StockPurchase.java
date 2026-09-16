package de.bierverein.api;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="stock_purchases")
public class StockPurchase {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Drink drink;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false, precision=10, scale=2) private BigDecimal unitPrice;
    @Column(nullable=false) private LocalDate purchaseDate;
    private String supplier;
    private String note;
    private String createdBy;
    @Column(precision=10, scale=3) private BigDecimal packageQuantity;
    @Column(precision=10, scale=6) private BigDecimal packageVolume;
    @Column(length=20) private String packageUnitShortName;

    public StockPurchase(){}
    public Long getId(){return id;}
    public Drink getDrink(){return drink;}
    public void setDrink(Drink v){drink=v;}
    public int getQuantity(){return quantity;}
    public void setQuantity(int v){quantity=v;}
    public BigDecimal getUnitPrice(){return unitPrice;}
    public void setUnitPrice(BigDecimal v){unitPrice=v;}
    public LocalDate getPurchaseDate(){return purchaseDate;}
    public void setPurchaseDate(LocalDate v){purchaseDate=v;}
    public String getSupplier(){return supplier;}
    public void setSupplier(String v){supplier=v;}
    public String getNote(){return note;}
    public void setNote(String v){note=v;}
    public String getCreatedBy(){return createdBy;}
    public void setCreatedBy(String v){createdBy=v;}
    public BigDecimal getPackageQuantity(){return packageQuantity;}
    public void setPackageQuantity(BigDecimal v){packageQuantity=v;}
    public BigDecimal getPackageVolume(){return packageVolume;}
    public void setPackageVolume(BigDecimal v){packageVolume=v;}
    public String getPackageUnitShortName(){return packageUnitShortName;}
    public void setPackageUnitShortName(String v){packageUnitShortName=v;}
}
