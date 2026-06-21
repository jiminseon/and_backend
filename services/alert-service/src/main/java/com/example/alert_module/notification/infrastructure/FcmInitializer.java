package com.example.alert_module.notification.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "real", matchIfMissing = true)
public class FcmInitializer {

    private final ResourceLoader resourceLoader;

    @Value("${fcm.firebase.config.path:}")
    private String firebaseConfigPath;

    public FcmInitializer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initialize() {
        try (InputStream stream = openFirebaseConfig()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase app initialized success: {}", firebaseConfigPath);
            } else {
                log.info("Firebase app already initialized.");
            }

        } catch (Exception e) {
            log.error("Error initializing Firebase app", e);
        }
    }

    private InputStream openFirebaseConfig() throws IOException {
        if (!StringUtils.hasText(firebaseConfigPath)) {
            throw new FileNotFoundException("Firebase config path is empty. Set fcm.firebase.config.path.");
        }

        Resource resource = resourceLoader.getResource(firebaseConfigPath);
        if (resource.exists()) {
            return resource.getInputStream();
        }

        Path path = Path.of(firebaseConfigPath);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }

        throw new FileNotFoundException("Firebase config file not found: " + firebaseConfigPath);
    }
}
