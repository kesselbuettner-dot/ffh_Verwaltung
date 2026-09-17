package de.bierverein.api;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.time.Instant; import java.util.*;
public interface OrderRepository extends JpaRepository<Order,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from Order o where o.id=:id") Optional<Order> findByIdForUpdate(@Param("id") Long id);
 List<Order> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from, Instant to);
 List<Order> findTop50ByMemberIdOrderByCreatedAtDesc(Long memberId);
 List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);
}
