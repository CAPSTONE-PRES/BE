package com.pres.pres_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PresServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PresServerApplication.class, args);
	}

}
