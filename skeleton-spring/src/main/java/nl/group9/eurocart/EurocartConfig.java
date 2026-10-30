package nl.group9.eurocart;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class EurocartConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
