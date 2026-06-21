package com.example.alert_module.notification.infrastructure;

import com.example.alert_module.notification.dto.PushMessage;

import java.util.List;

public interface FcmSender {

    FcmMulticastResult sendMulticast(List<String> tokens, PushMessage message);

    String send(String token, PushMessage message);
}
