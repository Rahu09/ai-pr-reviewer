package io.github.rahu09.prreviewer;

import org.springframework.boot.SpringApplication;

public class TestPrreviewerApplication {

	public static void main(String[] args) {
		SpringApplication.from(PrreviewerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
