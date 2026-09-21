package de.bierverein.api;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="inspection_job_items",uniqueConstraints=@UniqueConstraint(columnNames={"job_id","device_id"}))
public class InspectionJobItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="job_id") public InspectionJob job;
 @ManyToOne(optional=false) @JoinColumn(name="device_id") public Device device;
 @Column(nullable=false,length=150) public String deviceName;
 @Column(length=80) public String inventoryNumber;
 @Column(length=120) public String location;
 @Column(length=40) public String result;
 @Column(length=2000) public String notes;
 public OffsetDateTime checkedAt;
 @Column(length=120) public String checkedBy;
}
