package com.report;

import com.report.config.DatabaseMigrationRunner;
import com.report.config.SeederRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class ReportManageApplicationTests {

	@MockBean
	private DatabaseMigrationRunner databaseMigrationRunner;

	@MockBean
	private SeederRunner seederRunner;

	@Test
	void contextLoads() {
	}

}
