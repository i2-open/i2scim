package com.independentid.scim.server;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.ApplicationContext;

@ServletComponentScan
@SpringBootApplication
public class ScimBootApplication {

	private Logger logger = LoggerFactory.getLogger(ScimBootApplication.class);
	
	public static void main(String[] args) {	
		System.setProperty("server.servlet.context-path", "/scim");
	   
		SpringApplication.run(ScimBootApplication.class, args);
	}
	
	
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		
		logger.debug("ScimBootApplication initialization");
	    
		
		return args -> {
			//System.out.println("Let's inspect the beans provided by Spring Boot:");

			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				System.out.println(beanName);
			}
		};
	}
	
	/*
	@Bean
	public ServletRegistrationBean<ScimV2Servlet> servletRegistrationBean() {
		ServletRegistrationBean<ScimV2Servlet> bean = new ServletRegistrationBean<ScimV2Servlet>(
				new ScimV2Servlet(), "/");
		return bean;
	}
	*/
	
	
}
