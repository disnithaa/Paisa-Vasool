package com.paisavasool.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.domain.user.User;
import com.paisavasool.app.domain.user.UserRepository;

@Component
public class DevelopmentUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final boolean devAuthEnabled;
    private final String devEmail;
    private final String devName;

    public DevelopmentUserInitializer(
        UserRepository userRepository,
        @Value("${app.dev-auth.enabled:false}") boolean devAuthEnabled,
        @Value("${app.dev-auth.email:}") String devEmail,
        @Value("${app.dev-auth.name:Local Developer}") String devName
    ) {
        this.userRepository = userRepository;
        this.devAuthEnabled = devAuthEnabled;
        this.devEmail = devEmail;
        this.devName = devName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (devAuthEnabled && userRepository.findByEmailIgnoreCase(devEmail).isEmpty()) {
            userRepository.save(new User(devName, devEmail));
        }
    }
}
