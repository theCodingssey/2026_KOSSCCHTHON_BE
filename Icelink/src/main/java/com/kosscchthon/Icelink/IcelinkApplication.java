package com.kosscchthon.Icelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IcelinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(IcelinkApplication.class, args);
	}

}
