package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CalendarSubscriptionRepository extends JpaRepository<CalendarSubscription, Long> {
    Optional<CalendarSubscription> findByUsername(String username);
    Optional<CalendarSubscription> findByToken(String token);
}
