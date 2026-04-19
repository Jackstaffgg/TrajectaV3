package dev.knalis.trajectaapi.repo;

import dev.knalis.trajectaapi.model.notiffication.Notification;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientId(Long recipientId);

    List<Notification> findByRecipientIdAndIsRead(Long recipientId, boolean isRead);

    List<Notification> findBySenderIdAndTypeInOrderByCreatedAtDesc(Long senderId, List<NotificationType> types, Pageable pageable);
}


