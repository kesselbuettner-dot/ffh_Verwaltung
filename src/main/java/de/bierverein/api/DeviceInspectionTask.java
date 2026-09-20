package de.bierverein.api;
import jakarta.persistence.*;import java.time.*;
@Entity @Table(name="device_inspection_tasks",uniqueConstraints=@UniqueConstraint(columnNames={"event_id","occurrence_date","device_id"}))
public class DeviceInspectionTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId; @Column(name="occurrence_date",nullable=false) private LocalDate occurrenceDate;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="device_id") private Device device;
 @Column(nullable=false,length=24) private String status="PENDING"; @Column(length=1000) private String note;
 @Column(length=120) private String updatedBy; private Instant updatedAt;
 public Long getId(){return id;} public Long getEventId(){return eventId;} public void setEventId(Long v){eventId=v;} public LocalDate getOccurrenceDate(){return occurrenceDate;} public void setOccurrenceDate(LocalDate v){occurrenceDate=v;}
 public Device getDevice(){return device;} public void setDevice(Device v){device=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getNote(){return note;} public void setNote(String v){note=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
