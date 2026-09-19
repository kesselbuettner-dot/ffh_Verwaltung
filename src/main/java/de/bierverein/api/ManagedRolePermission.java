package de.bierverein.api;

import jakarta.persistence.*;

@Entity
@Table(name = "managed_role_permissions",
    uniqueConstraints = @UniqueConstraint(name = "uk_managed_role_permission",
        columnNames = {"role_id", "permission_key"}))
public class ManagedRolePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private ManagedRole role;
    @Column(name = "permission_key", nullable = false, length = 160)
    private String permissionKey;

    protected ManagedRolePermission() {}
    public ManagedRolePermission(ManagedRole role, String permissionKey) {
        if (role == null || permissionKey == null || !PermissionCatalog.keys().contains(permissionKey))
            throw new IllegalArgumentException("Unknown permission or missing role");
        this.role = role;
        this.permissionKey = permissionKey;
    }
    public Long getId() { return id; }
    public ManagedRole getRole() { return role; }
    public String getPermissionKey() { return permissionKey; }
}
