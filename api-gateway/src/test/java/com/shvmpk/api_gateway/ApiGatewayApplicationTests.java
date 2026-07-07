package com.shvmpk.api_gateway;

import com.shvmpk.api_gateway.config.FallbackController;
import com.shvmpk.api_gateway.config.RateLimitingGatewayFilterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void circuitBreakerFilterFactoryIsRegistered() {
		Map<String, GatewayFilterFactory> factories = context.getBeansOfType(GatewayFilterFactory.class);
		assertThat(factories).containsKey("springCloudCircuitBreakerResilience4JFilterFactory");
	}

	@Test
	void circuitBreakerFilterFactoryNameIsCircuitBreaker() {
		GatewayFilterFactory factory = (GatewayFilterFactory) context
				.getBean("springCloudCircuitBreakerResilience4JFilterFactory");
		assertThat(factory.name()).isEqualTo("CircuitBreaker");
	}

	@Test
	void rateLimitingFilterFactoryIsRegistered() {
		assertThat(context.getBean(RateLimitingGatewayFilterFactory.class)).isNotNull();
	}

	@Test
	void rateLimitingFilterFactoryNameIsRateLimiting() {
		var factory = context.getBean(RateLimitingGatewayFilterFactory.class);
		assertThat(factory.name()).isEqualTo("RateLimiting");
	}

	@Test
	void fallbackControllerIsRegistered() {
		assertThat(context.getBean(FallbackController.class)).isNotNull();
	}

}
