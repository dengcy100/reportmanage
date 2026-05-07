package com.plm.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class PlmReportBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlmReportBackendApplication.class, args);
	}

}
