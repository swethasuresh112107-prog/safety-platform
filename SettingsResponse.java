package com.guardianvoice.dto;

import com.guardianvoice.model.UserSettings;

public record SettingsResponse(
    String sosKeyword,
    String fakeCallKeyword,
    String callerName,
    boolean listenEnabled,
    boolean trackingEnabled
) {
    public static SettingsResponse from(UserSettings s) {
        return new SettingsResponse(
            s.getSosKeyword(), s.getFakeCallKeyword(), s.getCallerName(),
            s.isListenEnabled(), s.isTrackingEnabled()
        );
    }
}
