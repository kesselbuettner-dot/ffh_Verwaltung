package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeviceRepository extends JpaRepository<Device,Long>{
    Optional<Device> findFirstByBarcode(String barcode);
    Optional<Device> findFirstBySerialNumber(String serialNumber);
    Optional<Device> findFirstByBarcodeAndActiveTrue(String barcode);
    Optional<Device> findFirstBySerialNumberAndActiveTrue(String serialNumber);
    List<Device> findByActiveTrueOrderByNameAsc();
    boolean existsByCompartmentId(Long compartmentId);
    boolean existsByCompartmentIdAndActiveTrue(Long compartmentId);
    List<Device> findByCompartmentIdAndActiveTrueOrderByNameAsc(Long compartmentId);
}
