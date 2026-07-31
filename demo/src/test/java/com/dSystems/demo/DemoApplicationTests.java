package com.dSystems.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * THE SYSTEM STARTUP SANITY CHECK.
 * 
 * Think of this class as the "Engine Turn-On Test". 
 * It doesn't test any individual function or page. Instead, it attempts to boot up the entire 
 * Spring framework environment. If there are any broken configurations, typos in settings files, 
 * or missing database connections, the engine won't start, and this test will instantly fail, 
 * preventing broken builds from being deployed.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

    /**
     * Wakes up Spring and verifies that it can boot up successfully without throwing an error.
     */
	@Test
	void contextLoads() {
	}

}
