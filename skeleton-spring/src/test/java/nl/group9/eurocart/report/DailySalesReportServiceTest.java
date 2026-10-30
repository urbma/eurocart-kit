package nl.group9.eurocart.report;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.group9.eurocart.order.Order;
import nl.group9.eurocart.order.OrderRepository;
import nl.group9.eurocart.order.OrderService;
import nl.group9.eurocart.order.OrderStatus;
import nl.group9.eurocart.pricing.PricedCart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailySalesReportServiceTest {

	/** A paid order with a gross amount in a currency that was worth {@code eurPerUnit} EUR; the order date is the payment. */
	static Order paid(String id, String paidAt, String eurPerUnit, String gross) {
		PricedCart pricing = new PricedCart("PLN", eurPerUnit == null ? null : new BigDecimal(eurPerUnit), "2026-Q4",
				List.of(), null, new BigDecimal(gross), BigDecimal.ZERO, new BigDecimal(gross));
		return new Order(id, Instant.parse(paidAt).minusSeconds(5), OrderStatus.PAID, "a@b.pl", null, pricing, "SIM-" + id,
				Instant.parse(paidAt), Instant.parse(paidAt).minusSeconds(5));
	}

	static Order pending(String id, String createdAt) {
		PricedCart pricing = new PricedCart("EUR", BigDecimal.ONE, "2026-Q4", List.of(), null,
				new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("20.00"));
		return new Order(id, Instant.parse(createdAt), OrderStatus.PENDING, "a@b.pl", null, pricing, null, null,
				Instant.parse(createdAt));
	}

	static DailySalesReportService serviceWith(Order... saved) {
		OrderRepository repository = new OrderRepository() {
			@Override
			public void save(Order order) {
			}

			@Override
			public Optional<Order> find(String id) {
				return Optional.empty();
			}

			@Override
			public List<Order> findAll() {
				return List.of(saved);
			}
		};
		return new DailySalesReportService(new OrderService(null, null, repository, Clock.systemUTC(), order -> { }));
	}

	// 2026-10-30 is winter time in the Netherlands (UTC+1): that day runs from 2026-10-29T23:00Z until 2026-10-30T23:00Z
	private final DailySalesReportService service = serviceWith(
			paid("A", "2026-10-30T08:00:00Z", "0.33", "100.00"),    // 33.00 EUR
			paid("B", "2026-10-30T22:59:59Z", "1", "10.50"),         // 23:59:59 local: still the 30th
			paid("C", "2026-10-30T23:00:00Z", "1", "7.25"),          // 00:00:00 local: the 31st
			paid("D", "2026-10-29T22:59:59Z", "1", "99.00"),         // 23:59:59 local on the 29th
			pending("E", "2026-10-30T09:00:00Z"),                    // not paid: no sale
			paid("F", "2026-10-30T09:00:00Z", null, "50.00"),        // EUR value unknown: it was not announced either
			paid("G", "2026-10-30T10:00:00Z", "1.25", "9.99"));      // 12.4875 -> 12.49

	@Test
	void countsTheOrdersPaidOnThatDayInTheTimeZoneOfTheShop() {
		DailySalesReport report = service.reportFor(LocalDate.parse("2026-10-30"));

		assertThat(report.totaalOrders()).isEqualTo(3);
	}

	@Test
	void addsUpTheRoundedAmountOfEveryOrderInEuro() {
		// 33.00 + 10.50 + 12.49: each order is rounded on its own, exactly like the notification per order
		DailySalesReport report = service.reportFor(LocalDate.parse("2026-10-30"));

		assertThat(report.totaalOmzetInEuro()).isEqualTo(new BigDecimal("55.99"));
	}

	@Test
	void anOrderJustAfterMidnightBelongsToTheNextDay() {
		DailySalesReport report = service.reportFor(LocalDate.parse("2026-10-31"));

		assertThat(report.totaalOrders()).isEqualTo(1);
		assertThat(report.totaalOmzetInEuro()).isEqualTo(new BigDecimal("7.25"));
	}

	@Test
	void aDayWithoutOrdersIsReportedAsZero() {
		DailySalesReport report = service.reportFor(LocalDate.parse("2026-11-15"));

		assertThat(report.totaalOrders()).isZero();
		assertThat(report.totaalOmzetInEuro()).isEqualTo(new BigDecimal("0.00"));
	}

}
