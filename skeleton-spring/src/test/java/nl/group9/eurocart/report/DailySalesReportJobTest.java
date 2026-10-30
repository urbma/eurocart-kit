package nl.group9.eurocart.report;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static nl.group9.eurocart.report.DailySalesReportServiceTest.paid;
import static nl.group9.eurocart.report.DailySalesReportServiceTest.serviceWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailySalesReportJobTest {

	private static final String PATH_AND_QUERY = "/powerautomate/automations/direct/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sig=SECRET";

	private record Received(String method, String pathAndQuery, String contentType, String body) {
	}

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private int status = 202;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery(),
					exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + PATH_AND_QUERY;
	}

	private DailySalesReportJob job(String url, Instant now) {
		DailySalesReportService reports = serviceWith(
				paid("A", "2026-10-30T08:00:00Z", "0.33", "100.00"),
				paid("B", "2026-10-30T12:00:00Z", "1", "10.50"));
		return new DailySalesReportJob(url, new ObjectMapper(), reports, Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void postsTheTotalsOfTheDayAsJson() {
		boolean sent = job(url(), Instant.parse("2026-10-31T10:00:00Z")).send(LocalDate.parse("2026-10-30"));

		assertThat(sent).isTrue();
		assertThat(received).hasSize(1);
		Received request = received.get(0);
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.pathAndQuery()).isEqualTo(PATH_AND_QUERY);
		assertThat(request.contentType()).isEqualTo("application/json");
		assertThat(request.body()).isEqualTo("{\"totaalOrders\":2,\"totaalOmzetInEuro\":43.50}");
	}

	@Test
	void aDayWithoutOrdersIsSentAsZero() {
		job(url(), Instant.parse("2026-10-31T10:00:00Z")).send(LocalDate.parse("2026-11-15"));

		assertThat(received.get(0).body()).isEqualTo("{\"totaalOrders\":0,\"totaalOmzetInEuro\":0.00}");
	}

	@Test
	void theNightlyRunReportsYesterday() {
		job(url(), Instant.parse("2026-10-31T10:00:00Z")).sendYesterdaysReport();

		assertThat(received).hasSize(1);
		assertThat(received.get(0).body()).isEqualTo("{\"totaalOrders\":2,\"totaalOmzetInEuro\":43.50}");
	}

	@Test
	void yesterdayIsTheDayInTheNetherlandsNotInUtc() {
		// 23:30 UTC on the 30th is 00:30 on the 31st in the Netherlands: the day that just ended is the 30th
		job(url(), Instant.parse("2026-10-30T23:30:00Z")).sendYesterdaysReport();

		assertThat(received.get(0).body()).isEqualTo("{\"totaalOrders\":2,\"totaalOmzetInEuro\":43.50}");
	}

	@Test
	void aRefusedReportIsReportedNotThrown() {
		status = 500;

		assertThat(job(url(), Instant.parse("2026-10-31T10:00:00Z")).send(LocalDate.parse("2026-10-30"))).isFalse();
	}

	@Test
	void sendsNothingWithoutAUrl() {
		assertThat(job("", Instant.parse("2026-10-31T10:00:00Z")).send(LocalDate.parse("2026-10-30"))).isFalse();
		assertThat(received).isEmpty();
	}

	/** A clock the test can move. */
	private static final class MovableClock extends Clock {

		private Instant now;

		MovableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration by) {
			now = now.plus(by);
		}

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
			return now;
		}

	}

	private DailySalesReportJob jobWith(String url, Clock clock) {
		DailySalesReportService reports = serviceWith(paid("A", "2026-10-30T08:00:00Z", "0.33", "100.00"));
		return new DailySalesReportJob(url, new ObjectMapper(), reports, clock);
	}

	@Test
	void sendsTheReportOnRequestAndSaysWhatWasSent() {
		DailySalesReport report = jobWith(url(), Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC))
				.sendOnRequest(LocalDate.parse("2026-10-30"));

		assertThat(report.totaalOrders()).isEqualTo(1);
		assertThat(report.totaalOmzetInEuro()).isEqualTo(new BigDecimal("33.00"));
		assertThat(received).hasSize(1);
		assertThat(received.get(0).body()).isEqualTo("{\"totaalOrders\":1,\"totaalOmzetInEuro\":33.00}");
	}

	@Test
	void todayCanBeReportedButTheFutureCannot() {
		DailySalesReportJob job = jobWith(url(), Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));

		assertThat(job.sendOnRequest(LocalDate.parse("2026-10-30")).totaalOrders()).isEqualTo(1);
		assertThatThrownBy(() -> job.sendOnRequest(LocalDate.parse("2026-10-31")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A day in the future has no report yet");
	}

	@Test
	void aSecondRequestWithinTenSecondsIsRefusedAndNothingIsSent() {
		MovableClock clock = new MovableClock(Instant.parse("2026-10-31T10:00:00Z"));
		DailySalesReportJob job = jobWith(url(), clock);
		job.sendOnRequest(LocalDate.parse("2026-10-30"));

		clock.advance(Duration.ofSeconds(9));
		assertThatThrownBy(() -> job.sendOnRequest(LocalDate.parse("2026-10-30")))
				.isInstanceOf(DailyReportException.class)
				.extracting(ex -> ((DailyReportException) ex).reason()).isEqualTo(DailyReportException.Reason.TOO_SOON);
		assertThat(received).hasSize(1);

		// the refused request does not restart the wait: ten seconds after the report that was sent it works again
		clock.advance(Duration.ofSeconds(1));
		job.sendOnRequest(LocalDate.parse("2026-10-30"));
		assertThat(received).hasSize(2);
	}

	@Test
	void aRequestWithoutAUrlSaysThatTheReportIsNotSetUp() {
		DailySalesReportJob job = jobWith("", Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC));

		assertThatThrownBy(() -> job.sendOnRequest(LocalDate.parse("2026-10-30")))
				.isInstanceOf(DailyReportException.class)
				.extracting(ex -> ((DailyReportException) ex).reason()).isEqualTo(DailyReportException.Reason.NOT_CONFIGURED);
		assertThat(received).isEmpty();
	}

	@Test
	void aFlowThatDoesNotAcceptTheReportIsReportedToTheRequester() {
		status = 500;
		DailySalesReportJob job = jobWith(url(), Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC));

		assertThatThrownBy(() -> job.sendOnRequest(LocalDate.parse("2026-10-30")))
				.isInstanceOf(DailyReportException.class)
				.extracting(ex -> ((DailyReportException) ex).reason()).isEqualTo(DailyReportException.Reason.NOT_DELIVERED);
	}

	@Test
	void yesterdayIsCountedInTheTimeZoneOfTheShop() {
		assertThat(jobWith(url(), Clock.fixed(Instant.parse("2026-10-30T23:30:00Z"), ZoneOffset.UTC)).yesterday())
				.isEqualTo(LocalDate.parse("2026-10-30"));
		assertThat(jobWith(url(), Clock.fixed(Instant.parse("2026-10-30T22:30:00Z"), ZoneOffset.UTC)).yesterday())
				.isEqualTo(LocalDate.parse("2026-10-29"));
	}

	@Test
	void refusesAnInsecureUrlWithoutRevealingIt() {
		assertThatThrownBy(() -> job("http://example.com/flow?sig=SECRET", Instant.now()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("SECRET");
	}

}
