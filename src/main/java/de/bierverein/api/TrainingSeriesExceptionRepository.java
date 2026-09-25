package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TrainingSeriesExceptionRepository extends JpaRepository<TrainingSeriesException,Long> {
    boolean existsBySeriesEventIdAndOccurrenceDate(Long eventId, LocalDate date);
    List<TrainingSeriesException> findBySeriesEventId(Long eventId);
    void deleteBySeriesEventId(Long eventId);
}
