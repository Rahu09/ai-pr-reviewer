package io.github.rahu09.prreviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrreviewerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrreviewerApplication.class, args);
	}

}
