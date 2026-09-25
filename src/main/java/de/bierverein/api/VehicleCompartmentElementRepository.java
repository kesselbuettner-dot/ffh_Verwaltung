package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleCompartmentElementRepository extends JpaRepository<VehicleCompartmentElement,Long>{
 List<VehicleCompartmentElement> findByCompartmentIdOrderByLayerAscIdAsc(Long compartmentId);
}
