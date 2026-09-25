package de.bierverein.api;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireQualificationAttachmentRepository extends JpaRepository<FireQualificationAttachment,Long>{
 boolean existsByQualificationId(Long qualificationId);
 Optional<FireQualificationAttachment> findByQualificationId(Long qualificationId);
}
