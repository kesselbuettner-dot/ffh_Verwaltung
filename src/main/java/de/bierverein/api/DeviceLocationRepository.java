package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeviceLocationRepository extends JpaRepository<DeviceLocation,Long>{Optional<DeviceLocation> findByNameIgnoreCase(String name);List<DeviceLocation> findByActiveTrueOrderByNameAsc();}
