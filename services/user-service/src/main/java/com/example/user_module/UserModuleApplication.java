package com.example.user_module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ComponentScan(basePackages = {"com.example.user_module", "com.example.common_service"})
public class UserModuleApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserModuleApplication.class, args);
	}

}
