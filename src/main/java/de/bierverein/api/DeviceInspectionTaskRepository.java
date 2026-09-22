package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;import java.time.*;import java.util.*;
public interface DeviceInspectionTaskRepository extends JpaRepository<DeviceInspectionTask,Long>{List<DeviceInspectionTask> findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(Long eventId,LocalDate date);void deleteByEventId(Long eventId); List<DeviceInspectionTask> findByDeviceIdAndStatus(Long deviceId,String status); List<DeviceInspectionTask> findByDeviceIdAndInspectionIdIsNull(Long deviceId);}
