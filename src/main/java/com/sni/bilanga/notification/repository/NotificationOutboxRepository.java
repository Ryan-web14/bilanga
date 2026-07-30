package com.sni.bilanga.notification.repository;

import com.sni.bilanga.notification.model.NotificationOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    boolean existsByAlertIdAndChannel(Long alertId, String channel);

    /** File de reprise, du plus ancien au plus récent. */
    List<NotificationOutbox> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses, Pageable pageable);

    /**
     * File de reprise, report des heures de silence pris en compte.
     *
     * Une ligne dont {@code deferredUntil} court encore n'est pas prête : la
     * traiter reviendrait à annuler le report qui vient d'être décidé.
     */
    @Query("""
           select n from NotificationOutbox n
           where n.status in :statuses
             and (n.deferredUntil is null or n.deferredUntil <= :now)
           order by n.createdAt asc
           """)
    List<NotificationOutbox> findDispatchable(@Param("statuses") Collection<String> statuses,
                                              @Param("now") Instant now,
                                              Pageable pageable);

    /** Envoi encore en attente sur la même empreinte de regroupement. */
    Optional<NotificationOutbox> findFirstByGroupKeyAndChannelAndStatus(
            String groupKey, String channel, String status);

    @Query("""
           select n from NotificationOutbox n
           where (:status is null or upper(n.status) = :status)
             and (:channel is null or upper(n.channel) = :channel)
             and (:plotId is null or n.plotId = :plotId)
           """)
    Page<NotificationOutbox> search(@Param("status") String status,
                                    @Param("channel") String channel,
                                    @Param("plotId") Long plotId,
                                    Pageable pageable);
}
