package de.bierverein.api;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="inspection_jobs")
public class InspectionJob {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,length=160) public String title;
 @Column(nullable=false) public Long assigneeId;
 @Column(nullable=false,length=120) public String assigneeName;
 @Column(nullable=false,length=120) public String assignedBy;
 @Column(nullable=false) public OffsetDateTime createdAt=OffsetDateTime.now();
 public OffsetDateTime completedAt;
 @Column(length=120) public String signer;
 @Column(columnDefinition="text") public String signature;
}
