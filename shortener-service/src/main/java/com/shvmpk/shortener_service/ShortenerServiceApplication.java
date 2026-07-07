package com.shvmpk.shortener_service;

import com.shvmpk.shortener_service.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ShortenerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShortenerServiceApplication.class, args);
	}

}
