package com.centrral.centralres.features.contact.repository;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.centrral.centralres.features.contact.model.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);

    Page<ContactMessage> findByUserId(Long userId, Pageable pageable);
}
