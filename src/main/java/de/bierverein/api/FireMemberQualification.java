package de.bierverein.api;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="fire_member_qualifications",
 uniqueConstraints=@UniqueConstraint(columnNames={"member_id","qualification_type_id"}))
public class FireMemberQualification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="member_id",nullable=false) public Long memberId;
 @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="qualification_type_id",nullable=false)
 public FireQualificationType type;
 public LocalDate issuedOn;
 public LocalDate expiresOn;
 public LocalDate lastCheckedOn;
 public LocalDate nextDueOn;
 // HMAC of canonical license number; never the document photo or plaintext number.
 @Column(length=64) public String licenseNumberMac;
 @Column(length=180) public String licenseClasses;
 public boolean active=true;
 protected FireMemberQualification(){}
 public FireMemberQualification(Long memberId,FireQualificationType type){this.memberId=memberId;this.type=type;}
}
