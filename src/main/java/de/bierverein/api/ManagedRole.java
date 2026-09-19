package de.bierverein.api;

import jakarta.persistence.*;

@Entity
@Table(name = "managed_roles", uniqueConstraints = @UniqueConstraint(name = "uk_managed_roles_code", columnNames = "code"))
public class ManagedRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64, updatable = false)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private boolean systemRole;

    protected ManagedRole() {}
    public ManagedRole(String code, String name, String description, boolean systemRole) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new IllegalArgumentException("Invalid role code");
        if (name == null || name.isBlank() || name.length() > 120)
            throw new IllegalArgumentException("Invalid role name");
        this.code = code;
        this.name = name.trim();
        this.description = description;
        this.systemRole = systemRole;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isSystemRole() { return systemRole; }
    public void rename(String value) {
        if (value == null || value.isBlank() || value.length() > 120)
            throw new IllegalArgumentException("Invalid role name");
        name = value.trim();
    }
    public void setDescription(String value) { description = value; }
}
