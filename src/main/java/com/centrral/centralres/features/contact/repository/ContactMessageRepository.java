package com.centrral.centralres.features.contact.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import com.centrral.centralres.features.contact.model.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);
}
