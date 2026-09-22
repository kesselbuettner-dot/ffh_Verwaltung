package de.bierverein.api;
import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface DeviceCycleTaskRepository extends JpaRepository<DeviceCycleTask,Long>{
 Optional<DeviceCycleTask> findByDeviceIdAndDueOn(Long deviceId,LocalDate dueOn);
 List<DeviceCycleTask> findByStatusInOrderByDueOnAsc(Collection<String> statuses);
 List<DeviceCycleTask> findByAssignedUserIdAndStatusInOrderByDueOnAsc(Long userId,Collection<String> statuses);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select t from DeviceCycleTask t where t.id=:id")
 Optional<DeviceCycleTask> findLocked(@Param("id") Long id);
}
