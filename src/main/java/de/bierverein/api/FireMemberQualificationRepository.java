package de.bierverein.api;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireMemberQualificationRepository extends JpaRepository<FireMemberQualification,Long>{
 List<FireMemberQualification> findByMemberId(Long memberId);
 Optional<FireMemberQualification> findByMemberIdAndTypeCode(Long memberId,String code);
 List<FireMemberQualification> findAllByTypeCode(String code);
}
