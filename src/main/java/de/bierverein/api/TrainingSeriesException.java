package de.bierverein.api;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Original occurrence removed from a recurrence after detaching it as an editable event. */
@Entity
@Table(name="training_series_exceptions", uniqueConstraints=@UniqueConstraint(columnNames={"series_event_id","occurrence_date"}))
public class TrainingSeriesException {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="series_event_id",nullable=false) private Long seriesEventId;
    @Column(name="occurrence_date",nullable=false) private LocalDate occurrenceDate;
    @Column(name="detached_event_id",nullable=false) private Long detachedEventId;
    protected TrainingSeriesException() {}
    public TrainingSeriesException(Long seriesEventId,LocalDate occurrenceDate,Long detachedEventId){this.seriesEventId=seriesEventId;this.occurrenceDate=occurrenceDate;this.detachedEventId=detachedEventId;}
    public Long getSeriesEventId(){return seriesEventId;}
    public LocalDate getOccurrenceDate(){return occurrenceDate;}
    public Long getDetachedEventId(){return detachedEventId;}
}
