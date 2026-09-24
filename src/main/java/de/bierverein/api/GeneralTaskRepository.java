package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface GeneralTaskRepository extends JpaRepository<GeneralTask,Long> {
 List<GeneralTask> findByAssigneeIdOrCreatorIdOrderByDueOnAscCreatedAtDesc(Long assigneeId,Long creatorId);
 List<GeneralTask> findByStatusNotAndDueOnLessThanEqual(String status,LocalDate date);
}
