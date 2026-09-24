package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="event_presence",uniqueConstraints=@UniqueConstraint(name="uk_event_presence",columnNames={"event_id","occurrence_date","member_id"}))
public class EventPresence {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="event_id",nullable=false) public Long eventId;
 @Column(name="occurrence_date",nullable=false) public LocalDate occurrenceDate;
 @Column(name="member_id",nullable=false) public Long memberId;
 @Column(length=8,nullable=false) public String participation="OPEN";
 public LocalTime arrivedAt;
 public LocalTime leftAt;
 @Column(length=320) public String updatedBy;
 public Instant updatedAt=Instant.now();
 protected EventPresence(){}
 public EventPresence(Long eventId,LocalDate occurrenceDate,Long memberId){this.eventId=eventId;this.occurrenceDate=occurrenceDate;this.memberId=memberId;}
}
