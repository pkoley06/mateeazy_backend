package com.matcher.matchmate_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MatchmateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatchmateBackendApplication.class, args);
	}

}
