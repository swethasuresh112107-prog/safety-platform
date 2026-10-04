package com.guardianvoice.service;

import com.guardianvoice.dto.ContactRequest;
import com.guardianvoice.dto.ContactResponse;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.model.Contact;
import com.guardianvoice.model.User;
import com.guardianvoice.repository.ContactRepository;
import com.guardianvoice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    public ContactService(ContactRepository contactRepository, UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
    }

    public List<ContactResponse> list(Long userId) {
        return contactRepository.findByUserIdOrderByPrimaryDescNameAsc(userId)
            .stream().map(ContactResponse::from).toList();
    }

    public ContactResponse create(Long userId, ContactRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));

        Contact contact = new Contact(request.name(), request.phone(), request.relation(), request.primary(),
            request.liveLocationRecipient(), user);
        contactRepository.save(contact);
        return ContactResponse.from(contact);
    }

    public ContactResponse update(Long userId, Long contactId, ContactRequest request) {
        Contact contact = contactRepository.findByIdAndUserId(contactId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact not found."));

        contact.setName(request.name());
        contact.setPhone(request.phone());
        contact.setRelation(request.relation());
        contact.setPrimary(request.primary());
        contact.setLiveLocationRecipient(request.liveLocationRecipient());
        contactRepository.save(contact);
        return ContactResponse.from(contact);
    }

    public void delete(Long userId, Long contactId) {
        Contact contact = contactRepository.findByIdAndUserId(contactId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact not found."));
        contactRepository.delete(contact);
    }
}
