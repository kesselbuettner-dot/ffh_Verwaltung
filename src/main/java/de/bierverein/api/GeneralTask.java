package de.bierverein.api;

import jakarta.persistence.*;
import java.time.*;

/** General organisational task. Specialist tasks remain in their existing modules. */
@Entity
@Table(name="general_tasks",indexes={
 @Index(name="idx_general_tasks_assignee_status",columnList="assignee_id,status"),
 @Index(name="idx_general_tasks_due_status",columnList="due_on,status")
})
public class GeneralTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 public Long id;
 @Column(nullable=false,length=160) public String title;
 @Column(length=4000) public String description;
 @Column(name="assignee_id",nullable=false) public Long assigneeId;
 @Column(name="creator_id",nullable=false) public Long creatorId;
 @Column(name="due_on") public LocalDate dueOn;
 @Column(nullable=false,length=20) public String status="OPEN";
 @Column(nullable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 public Instant completedAt;
 public LocalDate lastDueReminderOn;
 protected GeneralTask(){}
 public GeneralTask(String title,String description,Long assigneeId,Long creatorId,LocalDate dueOn){
  this.title=title;this.description=description;this.assigneeId=assigneeId;this.creatorId=creatorId;this.dueOn=dueOn;
 }
}
