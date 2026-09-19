package de.bierverein.api;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedRoleRepository extends JpaRepository<ManagedRole, Long> {
    Optional<ManagedRole> findByCode(String code);
    boolean existsByCode(String code);
}
