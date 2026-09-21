package de.bierverein.api;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireMemberQualificationRepository extends JpaRepository<FireMemberQualification,Long>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select q from FireMemberQualification q where q.id=:id")
 Optional<FireMemberQualification> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
 List<FireMemberQualification> findByMemberId(Long memberId);
 Optional<FireMemberQualification> findByMemberIdAndTypeCode(Long memberId,String code);
 List<FireMemberQualification> findAllByTypeCode(String code);
}
