package de.bierverein.api;

import jakarta.persistence.*;
import java.time.*;

/** Immutable sign-off for one occurrence; kept even when the calendar event is removed. */
@Entity
@Table(name="device_inspection_session_reports",
 uniqueConstraints=@UniqueConstraint(name="uk_inspection_report_occurrence",columnNames={"event_id","occurrence_date"}))
public class DeviceInspectionSessionReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="occurrence_date",nullable=false) private LocalDate occurrenceDate;
 @Column(nullable=false,length=160) private String title;
 @Column(nullable=false,length=120) private String inspector;
 @Column(nullable=false,length=100000) private String signatureData;
 @Column(nullable=false) private Instant signedAt=Instant.now();
 protected DeviceInspectionSessionReport(){}
 public DeviceInspectionSessionReport(Long eventId,LocalDate occurrenceDate,String title,String inspector,String signatureData){
  this.eventId=eventId;this.occurrenceDate=occurrenceDate;this.title=title;this.inspector=inspector;this.signatureData=signatureData;
 }
 public Long getId(){return id;} public Long getEventId(){return eventId;}
 public LocalDate getOccurrenceDate(){return occurrenceDate;} public String getTitle(){return title;}
 public String getInspector(){return inspector;} public String getSignatureData(){return signatureData;}
 public Instant getSignedAt(){return signedAt;}
}
