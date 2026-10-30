package nl.group9.eurocart.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import nl.group9.eurocart.TestSalesNotifierConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The button on the finance page: the daily report on request, against a flow that is a local test server. */
@SpringBootTest(properties = "eurocart.sales-notification.url=")
@AutoConfigureMockMvc
@Import({TestSalesNotifierConfig.class, FinanceDailyReportApiTest.FixedClock.class})
class FinanceDailyReportApiTest {

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
		}

	}

	private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

	private static final HttpServer FLOW = startFlow();

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		registry.add("eurocart.daily-report.url", () -> "http://127.0.0.1:" + FLOW.getAddress().getPort() + "/flow?sig=SECRET");
		registry.add("eurocart.orders.dir", () -> {
			try {
				return Files.createTempDirectory("eurocart-daily-report-orders").toString();
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		});
	}

	@AfterAll
	static void stopFlow() {
		FLOW.stop(0);
	}

	@Test
	void sendsTheReportOfTheRequestedDayAndThenWaitsBeforeAllowingAnother() throws Exception {
		mockMvc.perform(post("/api/finance/daily-report").param("date", "2026-11-14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-11-14"))
				.andExpect(jsonPath("$.totaalOrders").value(0))
				.andExpect(jsonPath("$.totaalOmzetInEuro").value(0.00));
		assertThat(RECEIVED).containsExactly("{\"totaalOrders\":0,\"totaalOmzetInEuro\":0.00}");

		// pressing the button again straight away is refused and sends nothing
		mockMvc.perform(post("/api/finance/daily-report").param("date", "2026-11-14"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.detail").value("A report was sent a moment ago: wait 10 seconds"));
		assertThat(RECEIVED).hasSize(1);
	}

	@Test
	void aDayInTheFutureIsRefused() throws Exception {
		mockMvc.perform(post("/api/finance/daily-report").param("date", "2026-11-16"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A day in the future has no report yet"));
	}

	@Test
	void theReportCanOnlyBeAskedForWithPost() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/finance/daily-report"))
				.andExpect(status().is4xxClientError());
	}

	private static HttpServer startFlow() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				RECEIVED.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				exchange.sendResponseHeaders(202, -1);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
