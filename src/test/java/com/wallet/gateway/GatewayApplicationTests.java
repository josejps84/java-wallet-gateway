package com.wallet.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=test-secret-test-secret-test-secret-1234")
class GatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
