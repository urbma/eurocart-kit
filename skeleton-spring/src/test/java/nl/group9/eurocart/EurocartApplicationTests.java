package nl.group9.eurocart;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eurocart.sales-notification.url=", "eurocart.daily-report.url="})
class EurocartApplicationTests {

	@Test
	void contextLoads() {
	}

}
