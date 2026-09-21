package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="fire_qualification_reminders",uniqueConstraints=
 @UniqueConstraint(columnNames={"qualification_id","due_date","recipient"}))
public class FireQualificationReminder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="qualification_id",nullable=false) public Long qualificationId;
 @Column(name="due_date",nullable=false) public LocalDate dueDate;
 @Column(name="recipient",nullable=false,length=150) public String recipient;
 @Column(nullable=false) public Instant sentAt=Instant.now();
 protected FireQualificationReminder(){}
 public FireQualificationReminder(Long qualificationId,LocalDate dueDate,String recipient){
  this.qualificationId=qualificationId;this.dueDate=dueDate;this.recipient=recipient;
 }
}
