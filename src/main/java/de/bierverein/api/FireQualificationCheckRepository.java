package de.bierverein.api;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireQualificationCheckRepository extends JpaRepository<FireQualificationCheck,Long>{
 List<FireQualificationCheck> findByQualificationIdOrderByCheckedAtDesc(Long qualificationId);
 List<FireQualificationCheck> findAllByOrderByCheckedAtDesc();
 List<FireQualificationCheck> findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(java.time.Instant from, java.time.Instant until);
}
