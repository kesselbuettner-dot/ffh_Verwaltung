package de.bierverein.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedRolePermissionRepository extends JpaRepository<ManagedRolePermission, Long> {
    List<ManagedRolePermission> findByRoleId(Long roleId);
}
