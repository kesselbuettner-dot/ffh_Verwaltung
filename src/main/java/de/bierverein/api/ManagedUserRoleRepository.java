package de.bierverein.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedUserRoleRepository extends JpaRepository<ManagedUserRole, Long> {
    List<ManagedUserRole> findByUserId(Long userId);
    boolean existsByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    @org.springframework.data.jpa.repository.Query("select count(distinct a.user.id) from ManagedUserRole a where a.role.code = :code and a.role.systemRole = true and a.user.enabled = :enabled and a.user.registrationApproved = true")
    long countByRoleCodeAndUserEnabled(@org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("enabled") boolean enabled);
}
