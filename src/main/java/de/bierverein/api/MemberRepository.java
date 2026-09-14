package de.bierverein.api;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.util.*;
public interface MemberRepository extends JpaRepository<Member,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from Member m where m.id=:id") Optional<Member> findByIdForUpdate(@Param("id") Long id);
}
