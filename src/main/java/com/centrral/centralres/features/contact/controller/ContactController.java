package com.centrral.centralres.features.contact.controller;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.centrral.centralres.core.security.dto.ApiResponse;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.contact.dto.ContactRequest;
import com.centrral.centralres.features.contact.model.ContactMessage;
import com.centrral.centralres.features.contact.repository.ContactMessageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactController {
    private final ContactMessageRepository repository;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> send(@AuthenticationPrincipal User user,
            @Valid @RequestBody ContactRequest request) {
        Instant now = Instant.now();
        // Simple demo rate-limiting; production requires an external distributed limiter.
        if (repository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minusSeconds(60))) {
            return ResponseEntity.status(429)
                    .body(new ApiResponse<>(false, "Espera un minuto antes de enviar otro mensaje", null));
        }
        ContactMessage item = new ContactMessage();
        item.setUserId(user.getId());
        item.setSenderName(user.getFullName());
        item.setSenderEmail(user.getEmail());
        item.setSubject(request.getSubject().trim());
        item.setMessage(request.getMessage().trim());
        item.setCreatedAt(now);
        Long id = repository.save(item).getId();
        return ResponseEntity.ok(new ApiResponse<>(true, "Mensaje registrado para revisión", id));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ContactMessage>>> adminList(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Mensajes recibidos", repository.findAll(pageable)));
    }
}
