package dev.knalis.trajectaapi.model.notiffication;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity(name = "notifications")
public class Notification {
    
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;
    
    private String content;
    
    private Long recipientId;
    
    private Long senderId;
    
    private String senderName;
    
    private Long referenceId;
    
    @JsonProperty("isRead")
    private boolean isRead;
    
    private Instant createdAt;
    
}
