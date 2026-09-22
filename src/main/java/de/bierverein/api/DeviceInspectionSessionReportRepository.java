package de.bierverein.api;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeviceInspectionSessionReportRepository extends JpaRepository<DeviceInspectionSessionReport,Long>{
 Optional<DeviceInspectionSessionReport> findByEventIdAndOccurrenceDate(Long eventId,LocalDate date);
 List<DeviceInspectionSessionReport> findAllByOrderByOccurrenceDateDesc();
}
