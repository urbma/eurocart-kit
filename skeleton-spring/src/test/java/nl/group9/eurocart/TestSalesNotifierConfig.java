package nl.group9.eurocart;

import nl.group9.eurocart.notification.SalesNotifier;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestSalesNotifierConfig {

	@Bean
	@Primary
	RecordingSalesNotifier recordingSalesNotifier() {
		return new RecordingSalesNotifier();
	}

}
