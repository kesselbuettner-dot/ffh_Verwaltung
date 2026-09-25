package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FireVehicleRepository extends JpaRepository<FireVehicle,Long>{List<FireVehicle> findByActiveTrueOrderByNameAsc();}
