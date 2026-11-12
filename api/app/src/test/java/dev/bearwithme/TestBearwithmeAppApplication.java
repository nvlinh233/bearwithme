package dev.bearwithme;

import org.springframework.boot.SpringApplication;

public class TestBearwithmeAppApplication {

	public static void main(String[] args) {
		SpringApplication.from(BearwithmeAppApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
