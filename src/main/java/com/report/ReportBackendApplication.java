package com.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class ReportBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReportBackendApplication.class, args);
	}

}
