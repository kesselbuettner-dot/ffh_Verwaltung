package de.bierverein.api;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*;
@Entity @Table(name="orders")
public class Order {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Member member;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 @Column(nullable=false) private String createdBy;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private OrderStatus status=OrderStatus.COMPLETED;
 private Instant cancelledAt;
 private String cancelledBy;
 @Column(length=500) private String cancellationReason;
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) private List<OrderItem> items=new ArrayList<>();
 public Order(){} public Long getId(){return id;} public Member getMember(){return member;} public void setMember(Member v){member=v;} public BigDecimal getTotal(){return total;} public void setTotal(BigDecimal v){total=v;}
 public Instant getCreatedAt(){return createdAt;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;}
 public Instant getCancelledAt(){return cancelledAt;} public void setCancelledAt(Instant v){cancelledAt=v;} public String getCancelledBy(){return cancelledBy;} public void setCancelledBy(String v){cancelledBy=v;} public String getCancellationReason(){return cancellationReason;} public void setCancellationReason(String v){cancellationReason=v;}
 public List<OrderItem> getItems(){return items;} public void addItem(OrderItem i){items.add(i);i.setOrder(this);}
}
