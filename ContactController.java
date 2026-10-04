package com.guardianvoice.controller;

import com.guardianvoice.dto.ContactRequest;
import com.guardianvoice.dto.ContactResponse;
import com.guardianvoice.security.CurrentUser;
import com.guardianvoice.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<List<ContactResponse>> list() {
        return ResponseEntity.ok(contactService.list(CurrentUser.id()));
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponse> update(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.ok(contactService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contactService.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
