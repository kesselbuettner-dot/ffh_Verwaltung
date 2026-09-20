package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface VehicleCompartmentRepository extends JpaRepository<VehicleCompartment,Long>{List<VehicleCompartment> findByVehicleIdOrderByGridYAscGridXAsc(Long vehicleId);}
