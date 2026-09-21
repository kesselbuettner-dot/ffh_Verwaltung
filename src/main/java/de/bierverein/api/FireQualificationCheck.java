package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="fire_qualification_checks",indexes={@Index(columnList="memberId,checkedAt")})
public class FireQualificationCheck {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false) public Long memberId;
 @Column(nullable=false) public Long qualificationId;
 @Column(nullable=false) public Long checkedByUserId;
 @Column(nullable=false) public Instant checkedAt=Instant.now();
 @Column(nullable=false,length=16) public String method; // AUTO_OCR, MANUAL
 @Column(nullable=false,length=16) public String result; // POSITIVE
 @Column(length=50) public String checkReference; // optionally series period, no document number
 protected FireQualificationCheck(){}
 public FireQualificationCheck(Long memberId,Long qualificationId,Long actor,String method,String reference){
  this.memberId=memberId;this.qualificationId=qualificationId;this.checkedByUserId=actor;this.method=method;this.result="POSITIVE";this.checkReference=reference;
 }
}
