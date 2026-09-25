package de.bierverein.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface DashboardMessageReadRepository extends JpaRepository<DashboardMessageRead, Long> {
    boolean existsByMessageIdAndUsername(Long messageId, String username);
    void deleteByMessageId(Long messageId);

    @Query("select r.messageId from DashboardMessageRead r where r.username = :username and r.messageId in :ids")
    List<Long> findReadMessageIds(@Param("username") String username, @Param("ids") Collection<Long> ids);
}
