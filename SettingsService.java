package com.guardianvoice.service;

import com.guardianvoice.dto.SettingsRequest;
import com.guardianvoice.dto.SettingsResponse;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.model.User;
import com.guardianvoice.model.UserSettings;
import com.guardianvoice.repository.UserRepository;
import com.guardianvoice.repository.UserSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public SettingsService(UserSettingsRepository settingsRepository, UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    public SettingsResponse get(Long userId) {
        return SettingsResponse.from(getOrCreate(userId));
    }

    public SettingsResponse update(Long userId, SettingsRequest request) {
        UserSettings settings = getOrCreate(userId);
        settings.setSosKeyword(request.sosKeyword());
        settings.setFakeCallKeyword(request.fakeCallKeyword());
        settings.setCallerName(request.callerName());
        settings.setListenEnabled(request.listenEnabled());
        settings.setTrackingEnabled(request.trackingEnabled());
        settingsRepository.save(settings);
        return SettingsResponse.from(settings);
    }

    private UserSettings getOrCreate(Long userId) {
        return settingsRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));
            return settingsRepository.save(new UserSettings(user));
        });
    }
}
