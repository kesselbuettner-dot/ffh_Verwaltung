package de.bierverein.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedUserRoleRepository extends JpaRepository<ManagedUserRole, Long> {
    List<ManagedUserRole> findByUserId(Long userId);
}
