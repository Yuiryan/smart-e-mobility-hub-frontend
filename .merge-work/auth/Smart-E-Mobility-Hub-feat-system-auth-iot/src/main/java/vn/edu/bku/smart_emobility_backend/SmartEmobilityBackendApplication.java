package vn.edu.bku.smart_emobility_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SmartEmobilityBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartEmobilityBackendApplication.class, args);
	}

}
