package de.bierverein.api;

import jakarta.persistence.*;

@Entity
@Table(name = "managed_user_roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_managed_user_role",
        columnNames = {"user_id", "role_id"}))
public class ManagedUserRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private ManagedRole role;

    protected ManagedUserRole() {}
    public ManagedUserRole(AppUser user, ManagedRole role) {
        if (user == null || role == null) throw new IllegalArgumentException("User and role are required");
        this.user = user;
        this.role = role;
    }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public ManagedRole getRole() { return role; }
}
