package de.bierverein.api;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="fire_qualification_attachments",uniqueConstraints=@UniqueConstraint(columnNames="qualification_id"))
public class FireQualificationAttachment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="qualification_id",nullable=false,unique=true) public Long qualificationId;
 @Column(nullable=false,length=160) public String filename;
 @Column(nullable=false) public Instant uploadedAt=Instant.now();
 @Column(nullable=false,columnDefinition="bytea") public byte[] bytes;
 protected FireQualificationAttachment(){}
 public FireQualificationAttachment(Long qualificationId,String filename,byte[] bytes){
  this.qualificationId=qualificationId;this.filename=filename;this.bytes=bytes;
 }
}
