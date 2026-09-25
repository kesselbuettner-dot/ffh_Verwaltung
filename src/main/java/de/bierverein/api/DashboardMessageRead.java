package de.bierverein.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dashboard_message_reads", uniqueConstraints = @UniqueConstraint(
        name = "uk_dashboard_message_read", columnNames = {"message_id", "username"}))
public class DashboardMessageRead {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(nullable = false, length = 320)
    private String username;
    @Column(nullable = false)
    private Instant readAt = Instant.now();

    protected DashboardMessageRead() {}
    public DashboardMessageRead(Long messageId, String username) {
        this.messageId = messageId;
        this.username = username;
    }
    public Long getMessageId() { return messageId; }
    public String getUsername() { return username; }
    public Instant getReadAt() { return readAt; }
}
