package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InspectionJobRepository extends JpaRepository<InspectionJob,Long> {
 List<InspectionJob> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);
 List<InspectionJob> findAllByOrderByCreatedAtDesc();
}
