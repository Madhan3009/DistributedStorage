package com.dSystems.demo;

import com.dSystems.demo.Config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * THIS IS THE MAIN ENTRY POINT OF THE APPLICATION.
 * 
 * Think of this class as the "ignition key" or the "power switch" for our entire program.
 * When you run the project, this is the very first piece of code that starts executing.
 * 
 * Explanations of the special labels (called "Annotations" starting with @) above the class:
 * - @SpringBootApplication: Tells the computer that this is a Spring Boot app. It automatically
 *   configures a web server, connects to database helpers, and wires up code components.
 * - @EnableScheduling: Turns on the app's internal alarm clock. This allows other parts of the
 *   program to run tasks automatically at set intervals (like checking if storage servers are still online).
 * - @EnableConfigurationProperties(StorageProperties.class): Tells the app to load custom configuration settings
 *   (like folder directories for file storage) and make them available to the code.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StorageProperties.class)
public class DemoApplication {

	/**
	 * The main start button.
	 * When the program is launched, the computer looks for this exact block to start running things.
	 * 
	 * @param args Startup options that can be passed to the program from the command line.
	 */
	public static void main(String[] args) {
		// This command kicks off the Spring framework to configure, start up, and run our web application.
		SpringApplication.run(DemoApplication.class, args);
	}

}

