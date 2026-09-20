package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {
    List<TrainingAttendance> findByEventIdAndOccurrenceDate(Long eventId, LocalDate occurrenceDate);
    Optional<TrainingAttendance> findByEventIdAndOccurrenceDateAndUsername(Long eventId, LocalDate occurrenceDate, String username);
    void deleteByEventId(Long eventId);
}
