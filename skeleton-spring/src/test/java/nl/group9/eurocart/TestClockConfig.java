package nl.group9.eurocart;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Makes tests independent of the day they run on: the exchange rate of an order depends on its date, so the real
 * date would make the tests fail as soon as a quarter without a rate table starts. The clock starts in 2026-Q4 and
 * moves on one second per reading, so orders get different, increasing timestamps.
 */
@TestConfiguration
public class TestClockConfig {

	public static final Instant START = Instant.parse("2026-11-15T12:00:00Z");

	@Bean
	@Primary
	Clock tickingClock() {
		AtomicLong seconds = new AtomicLong();
		return new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return START.plusSeconds(seconds.getAndIncrement());
			}
		};
	}

}
