package de.bierverein.api;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="credit_topups")
public class CreditTopup {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Member member;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 @Column(nullable=false,unique=true,length=64) private String referenceCode;
 @Column(nullable=false,length=32) private String status="PENDING";
 @Column(nullable=false,length=32) private String paymentMethod="PAYPAL_DONATION";
 @Column(nullable=false) private Instant createdAt=Instant.now();
 private Instant reportedAt,reviewedAt;
 private String reviewedBy,createdBy;
 @Column(length=500) private String note;
 public Long getId(){return id;} public Member getMember(){return member;} public void setMember(Member v){member=v;}
 public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
 public String getReferenceCode(){return referenceCode;} public void setReferenceCode(String v){referenceCode=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){paymentMethod=v;}
 public Instant getCreatedAt(){return createdAt;} public Instant getReportedAt(){return reportedAt;} public void setReportedAt(Instant v){reportedAt=v;}
 public Instant getReviewedAt(){return reviewedAt;} public void setReviewedAt(Instant v){reviewedAt=v;}
 public String getReviewedBy(){return reviewedBy;} public void setReviewedBy(String v){reviewedBy=v;}
 public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
 public String getNote(){return note;} public void setNote(String v){note=v;}
}