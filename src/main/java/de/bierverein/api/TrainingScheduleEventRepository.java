package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TrainingScheduleEventRepository extends JpaRepository<TrainingScheduleEvent, Long> {
    List<TrainingScheduleEvent> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
            LocalDate to, LocalDate from);
}
