package com.guardianvoice.dto;

import com.guardianvoice.model.Contact;

public record ContactResponse(Long id, String name, String phone, String relation, boolean primary,
                               boolean liveLocationRecipient) {
    public static ContactResponse from(Contact c) {
        return new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getRelation(), c.isPrimary(),
            c.isLiveLocationRecipient());
    }
}
