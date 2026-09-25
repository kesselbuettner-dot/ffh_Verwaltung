package de.bierverein.api;

import jakarta.persistence.*;
import java.time.*;

/** One generated device-cycle task per device and due date, independent of event-based group inspections. */
@Entity
@Table(name="device_cycle_tasks",
  uniqueConstraints=@UniqueConstraint(name="uk_device_cycle_due",columnNames={"device_id","due_on"}),
  indexes={@Index(name="idx_cycle_task_assignee",columnList="assigned_user_id")})
public class DeviceCycleTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="device_id",nullable=false) private Device device;
 @Column(name="due_on",nullable=false) private LocalDate dueOn;
 @Column(nullable=false,length=24) private String status="OPEN";
 @Column(name="assigned_user_id") private Long assignedUserId;
 @Column(length=120) private String assignedBy;
 private Instant assignedAt;
 @Column(length=1000) private String note;
 @Column(length=24) private String result;
 @Column(length=120) private String completedBy;
 private Instant completedAt;
 private Long inspectionId;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 protected DeviceCycleTask(){}
 public DeviceCycleTask(Device device,LocalDate dueOn){this.device=device;this.dueOn=dueOn;}
 public Long getInspectionId(){return inspectionId;} public void setInspectionId(Long v){inspectionId=v;}
 public Long getId(){return id;}public Device getDevice(){return device;}
 public LocalDate getDueOn(){return dueOn;}public String getStatus(){return status;}
 public void setStatus(String value){status=value;}
 public Long getAssignedUserId(){return assignedUserId;}
 public void assign(Long userId,String actor){assignedUserId=userId;assignedBy=actor;assignedAt=Instant.now();}
 public String getAssignedBy(){return assignedBy;}public Instant getAssignedAt(){return assignedAt;}
 public String getNote(){return note;}public String getResult(){return result;}
 public String getCompletedBy(){return completedBy;}public Instant getCompletedAt(){return completedAt;}
 public void complete(String result,String note,String actor){this.result=result;this.note=note;this.completedBy=actor;this.completedAt=Instant.now();status="DONE";}
}
