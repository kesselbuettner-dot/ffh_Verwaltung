package de.bierverein.api;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InventoryCountRepository extends JpaRepository<InventoryCount,Long>{
    List<InventoryCount> findTop100ByOrderByCountedAtDesc();
}
