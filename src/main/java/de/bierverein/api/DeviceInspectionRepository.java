package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeviceInspectionRepository extends JpaRepository<DeviceInspection,Long>{
    List<DeviceInspection> findByDeviceIdOrderByInspectionDateDesc(Long deviceId);
    List<DeviceInspection> findBySessionReportIdOrderByIdAsc(Long reportId);
}
