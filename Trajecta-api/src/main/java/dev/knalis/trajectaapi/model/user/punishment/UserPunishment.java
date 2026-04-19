package dev.knalis.trajectaapi.model.user.punishment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.knalis.trajectaapi.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(indexes = {
        @Index(name = "idx_user_punishment_user", columnList = "user_id"),
        @Index(name = "idx_user_punishment_expired", columnList = "expired_at"),
        @Index(name = "idx_user_punishment_punished_by", columnList = "punished_by")
})
public class UserPunishment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY,  optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punished_by", nullable = false)
    private User punishedBy;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PunishmentType type;
    
    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "expired_at")
    private Instant expiredAt;
    
    
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
    
    @Transient
    public boolean isExpired() {
        return expiredAt != null && !expiredAt.isAfter(Instant.now());
    }
    
}
