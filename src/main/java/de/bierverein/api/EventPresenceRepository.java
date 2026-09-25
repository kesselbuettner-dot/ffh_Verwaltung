package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface EventPresenceRepository extends JpaRepository<EventPresence,Long> {
 List<EventPresence> findByEventIdAndOccurrenceDate(Long eventId,LocalDate date);
 Optional<EventPresence> findByEventIdAndOccurrenceDateAndMemberId(Long eventId,LocalDate date,Long memberId);
}
