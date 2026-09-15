package de.bierverein.api;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="order_items")
public class OrderItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Order order;
 /** Legacy reference for orders created before the central article master. */
 @ManyToOne(optional=true,fetch=FetchType.LAZY) private Drink drink;
 /** Central article reference for new orders. */
 @ManyToOne(optional=true,fetch=FetchType.LAZY) private Article article;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=10,scale=2) private BigDecimal unitPrice;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
 public OrderItem(){}
 public Long getId(){return id;}
 public Order getOrder(){return order;} public void setOrder(Order v){order=v;}
 public Drink getDrink(){return drink;} public void setDrink(Drink v){drink=v;}
 public Article getArticle(){return article;} public void setArticle(Article v){article=v;}
 public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
 public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
 public BigDecimal getTotal(){return total;} public void setTotal(BigDecimal v){total=v;}
}
