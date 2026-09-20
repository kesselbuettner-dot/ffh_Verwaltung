package de.bierverein.api;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "training_attendance", uniqueConstraints = @UniqueConstraint(
        name = "uk_training_attendance", columnNames = {"event_id", "occurrence_date", "username"}))
public class TrainingAttendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "occurrence_date", nullable = false) private LocalDate occurrenceDate;
    @Column(nullable = false, length = 320) private String username;
    private Long memberId;
    @Column(nullable = false, length = 10) private String status;
    @Column(nullable = false) private Instant respondedAt = Instant.now();

    protected TrainingAttendance() {}
    public TrainingAttendance(Long eventId,LocalDate occurrenceDate,String username,Long memberId,String status){this.eventId=eventId;this.occurrenceDate=occurrenceDate;this.username=username;this.memberId=memberId;this.status=status;}
    public Long getEventId(){return eventId;} public LocalDate getOccurrenceDate(){return occurrenceDate;} public String getUsername(){return username;} public Long getMemberId(){return memberId;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;respondedAt=Instant.now();} public Instant getRespondedAt(){return respondedAt;}
}
