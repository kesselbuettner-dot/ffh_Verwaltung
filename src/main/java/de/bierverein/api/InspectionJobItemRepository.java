package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface InspectionJobItemRepository extends JpaRepository<InspectionJobItem,Long> {
 List<InspectionJobItem> findByJobIdOrderByIdAsc(Long jobId);
 Optional<InspectionJobItem> findByIdAndJobId(Long id,Long jobId);
}
