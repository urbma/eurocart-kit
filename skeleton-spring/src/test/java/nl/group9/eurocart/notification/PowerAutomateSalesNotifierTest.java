package nl.group9.eurocart.notification;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import nl.group9.eurocart.order.Order;
import nl.group9.eurocart.order.OrderStatus;
import nl.group9.eurocart.pricing.PricedCart;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PowerAutomateSalesNotifierTest {

	/** Looks like the real flow URL: an encoded path inside the query and a secret signature. */
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

	private PowerAutomateSalesNotifier notifier(String url) {
		return new PowerAutomateSalesNotifier(url, new ObjectMapper());
	}

	private static Order order(String eurPerUnit, String gross) {
		PricedCart pricing = new PricedCart("PLN", eurPerUnit == null ? null : new BigDecimal(eurPerUnit), "2026-Q3",
				List.of(), null, new BigDecimal(gross), BigDecimal.ZERO, new BigDecimal(gross));
		return new Order("order-1", Instant.parse("2026-07-30T12:00:00Z"), OrderStatus.PAID, "a@b.pl", null, pricing,
				"SIM-1", Instant.parse("2026-07-30T12:00:05Z"), Instant.parse("2026-07-30T12:00:00Z"));
	}

	@Test
	void postsTheAmountInEurAsJsonToTheFlowUrl() {
		// 68.43 PLN * 0.23 = 15.7389 EUR, rounded to 15.74 in Java
		boolean sent = notifier(url()).send(order("0.23", "68.43"));

		assertThat(sent).isTrue();
		assertThat(received).hasSize(1);
		Received request = received.get(0);
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.pathAndQuery()).isEqualTo(PATH_AND_QUERY);
		assertThat(request.contentType()).isEqualTo("application/json");
		assertThat(request.body()).isEqualTo("{\"bedrag\":15.74,\"valuta\":\"EUR\"}");
	}

	@Test
	void alwaysSendsTwoDecimals() {
		// an euro order: 9.80 must not become 9.8
		notifier(url()).send(order("1", "9.80"));

		assertThat(received.get(0).body()).isEqualTo("{\"bedrag\":9.80,\"valuta\":\"EUR\"}");
	}

	@Test
	void roundsHalfUpToTwoDecimals() {
		// 10.01 * 0.5 = 5.005 -> 5.01
		notifier(url()).send(order("0.5", "10.01"));

		assertThat(received.get(0).body()).isEqualTo("{\"bedrag\":5.01,\"valuta\":\"EUR\"}");
	}

	@Test
	void aRefusedNotificationIsReportedNotThrown() {
		status = 500;

		assertThat(notifier(url()).send(order("1", "9.80"))).isFalse();
	}

	@Test
	void anUnreachableFlowIsReportedNotThrown() {
		String unreachable = "http://127.0.0.1:1" + PATH_AND_QUERY;

		assertThat(notifier(unreachable).send(order("1", "9.80"))).isFalse();
	}

	@Test
	void anOrderWithoutExchangeRateIsSkipped() {
		assertThat(notifier(url()).send(order(null, "9.80"))).isFalse();
		assertThat(received).isEmpty();
	}

	@Test
	void sendsNothingWithoutAUrl() throws InterruptedException {
		notifier("").orderPaid(order("1", "9.80"));
		notifier("  ").orderPaid(order("1", "9.80"));
		Thread.sleep(200);

		assertThat(received).isEmpty();
	}

	@Test
	void orderPaidSendsInTheBackground() throws InterruptedException {
		notifier(url()).orderPaid(order("1", "9.80"));

		for (int i = 0; i < 50 && received.isEmpty(); i++) {
			Thread.sleep(100);
		}
		assertThat(received).hasSize(1);
	}

	@Test
	void refusesAnInsecureOrBrokenUrlWithoutRevealingIt() {
		assertThatThrownBy(() -> notifier("http://example.com/flow?sig=SECRET"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("SECRET");
		assertThatThrownBy(() -> notifier("not a url ?sig=SECRET"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("SECRET");
	}

}
