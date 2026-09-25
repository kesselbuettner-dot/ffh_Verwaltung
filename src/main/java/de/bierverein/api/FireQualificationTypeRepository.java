package de.bierverein.api;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FireQualificationTypeRepository extends JpaRepository<FireQualificationType,Long>{
 Optional<FireQualificationType> findByCode(String code);
}
