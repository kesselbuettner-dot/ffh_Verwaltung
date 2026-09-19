package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeviceRepository extends JpaRepository<Device,Long>{
    Optional<Device> findFirstByBarcode(String barcode);
    Optional<Device> findFirstBySerialNumber(String serialNumber);
    List<Device> findByActiveTrueOrderByNameAsc();
}
