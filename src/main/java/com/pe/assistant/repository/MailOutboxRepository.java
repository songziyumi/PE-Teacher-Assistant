package com.pe.assistant.repository;

import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.MailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    @Query("""
            select mail
            from MailOutbox mail
            where mail.status = :status
              and (mail.nextRetryAt is null or mail.nextRetryAt <= :now)
            order by mail.createdAt asc, mail.id asc
            """)
    List<MailOutbox> findDispatchBatch(@Param("status") MailOutboxStatus status,
                                       @Param("now") LocalDateTime now,
                                       Pageable pageable);
}
