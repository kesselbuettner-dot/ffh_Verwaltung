package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription,Long>{
 Optional<PushSubscription> findByEndpoint(String endpoint);
 java.util.List<PushSubscription> findByUsername(String username);
 void deleteByEndpointAndUsername(String endpoint,String username);
}
