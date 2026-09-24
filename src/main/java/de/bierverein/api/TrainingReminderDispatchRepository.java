package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface TrainingReminderDispatchRepository extends JpaRepository<TrainingReminderDispatch,Long> {
 boolean existsByEventIdAndOccurrenceDateAndUsername(Long eventId,LocalDate date,String username);
}
