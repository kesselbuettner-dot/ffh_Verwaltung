package de.bierverein.api;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface CreditTopupRepository extends JpaRepository<CreditTopup,Long>{
 List<CreditTopup> findByMemberIdOrderByCreatedAtDesc(Long memberId);
 List<CreditTopup> findByStatusOrderByCreatedAtAsc(String status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from CreditTopup t where t.id=:id") Optional<CreditTopup> findByIdForUpdate(@Param("id") Long id);
}