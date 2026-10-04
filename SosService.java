package com.guardianvoice.service;

import com.guardianvoice.dto.SosRequest;
import com.guardianvoice.dto.SosResponse;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.model.Contact;
import com.guardianvoice.model.Emergency;
import com.guardianvoice.model.User;
import com.guardianvoice.repository.ContactRepository;
import com.guardianvoice.repository.SosRepository;
import com.guardianvoice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SosService {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    private final SosRepository sosRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    public SosService(SosRepository sosRepository, ContactRepository contactRepository, UserRepository userRepository) {
        this.sosRepository = sosRepository;
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
    }

    public SosResponse trigger(Long userId, SosRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));

        Emergency emergency = new Emergency(
            request.lat(), request.lng(), request.message(),
            request.nearestStationName(), request.nearestStationPhone(),
            request.stationLat(), request.stationLng(), user
        );
        sosRepository.save(emergency);

        notifyContacts(user, emergency);
        notifyNearestStation(user, emergency);

        return SosResponse.from(emergency);
    }

    public SosResponse cancel(Long userId, Long sosId) {
        Emergency emergency = sosRepository.findByIdAndUserId(sosId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found."));

        emergency.setStatus(Emergency.Status.CANCELLED);
        sosRepository.save(emergency);
        return SosResponse.from(emergency);
    }

    public List<SosResponse> history(Long userId) {
        return sosRepository.findByUserIdOrderByTimestampDesc(userId)
            .stream().map(SosResponse::from).toList();
    }

    /**
     * Integration point for real notifications. Wire in an SMS/push/email
     * provider here (e.g. Twilio, Firebase Cloud Messaging, SES). For now
     * this just logs so the rest of the flow is testable end-to-end.
     */
    private void notifyContacts(User user, Emergency emergency) {
        List<Contact> contacts = contactRepository.findByUserIdOrderByPrimaryDescNameAsc(user.getId());
        for (Contact contact : contacts) {
            log.info("SOS #{} from {}: notifying {} ({}) at {}, {}",
                emergency.getId(), user.getName(), contact.getName(), contact.getPhone(),
                emergency.getLat(), emergency.getLng());
        }
    }

    /**
     * Integration point for real police notification. The frontend looks up
     * the nearest station (via OpenStreetMap) and sends its name/phone along
     * with the alert; this just logs it for now.
     *
     * Important: there is no public API that lets a private app auto-dispatch
     * police. Real deployments either (a) route this through a licensed
     * emergency-services aggregator/PSAP integration, or (b) treat this as
     * "one-tap calling" -- the number is handed to the user/contact to call
     * directly (in India, the emergency number is 112). Don't represent this
     * stub as an actual police dispatch to end users.
     */
    private void notifyNearestStation(User user, Emergency emergency) {
        if (emergency.getNearestStationName() == null) return;
        log.info("SOS #{} from {}: nearest station {} ({}) at {}, {}",
            emergency.getId(), user.getName(), emergency.getNearestStationName(),
            emergency.getNearestStationPhone(), emergency.getStationLat(), emergency.getStationLng());
    }
}
