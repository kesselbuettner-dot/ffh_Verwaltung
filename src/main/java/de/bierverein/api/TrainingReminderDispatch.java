package de.bierverein.api;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="training_reminder_dispatch",uniqueConstraints=@UniqueConstraint(name="uk_training_reminder_dispatch",columnNames={"event_id","occurrence_date","username"}))
public class TrainingReminderDispatch {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="occurrence_date",nullable=false) private LocalDate occurrenceDate;
 @Column(nullable=false,length=320) private String username;
 @Column(nullable=false) private Instant sentAt=Instant.now();
 protected TrainingReminderDispatch(){}
 public TrainingReminderDispatch(Long eventId,LocalDate occurrenceDate,String username){this.eventId=eventId;this.occurrenceDate=occurrenceDate;this.username=username;}
}
