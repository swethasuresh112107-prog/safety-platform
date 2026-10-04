package com.guardianvoice.repository;

import com.guardianvoice.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByUserIdOrderByPrimaryDescNameAsc(Long userId);
    Optional<Contact> findByIdAndUserId(Long id, Long userId);
    List<Contact> findByUserIdAndLiveLocationRecipientTrue(Long userId);
}
