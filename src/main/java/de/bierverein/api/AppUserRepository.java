package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("""
        select u
        from AppUser u
        left join fetch u.member
        where lower(u.username) = lower(:username)
    """)
    Optional<AppUser> findByUsernameWithMember(@Param("username") String username);

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
