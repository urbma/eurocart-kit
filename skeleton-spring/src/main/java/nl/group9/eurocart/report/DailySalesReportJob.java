package nl.group9.eurocart.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import nl.group9.eurocart.notification.FlowClient;
import nl.group9.eurocart.pricing.ExchangeRates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Every night, shortly after midnight in the Netherlands, sends the sales of the day that just ended to the Power
 * Automate flow of the daily report. A day without orders is reported too: zero orders, zero revenue. The report is
 * not repeated for a night the application was not running. Without a URL nothing is sent.
 */
@Component
public class DailySalesReportJob {

	private static final Logger log = LoggerFactory.getLogger(DailySalesReportJob.class);

	private final FlowClient flow;

	private final DailySalesReportService reports;

	private final Clock clock;

	/** When a report was last sent on request: the report of a day can only be asked for now and then. */
	private final AtomicReference<Instant> lastRequested = new AtomicReference<>();

	static final Duration MINIMUM_TIME_BETWEEN_REQUESTS = Duration.ofSeconds(10);

	public DailySalesReportJob(@Value("${eurocart.daily-report.url:}") String url, ObjectMapper objectMapper,
			DailySalesReportService reports, Clock clock) {
		this.flow = new FlowClient(url, "eurocart.daily-report.url", objectMapper);
		this.reports = reports;
		this.clock = clock;
		if (!flow.configured()) {
			log.info("No daily report URL configured: the daily sales report is not sent");
		}
	}

	@Scheduled(cron = "${eurocart.daily-report.cron:0 5 0 * * *}", zone = ExchangeRates.SHOP_ZONE_ID)
	void sendYesterdaysReport() {
		send(yesterday());
	}

	/** The day that has just ended, in the time zone of the shop. */
	public LocalDate yesterday() {
		return clock.instant().atZone(ExchangeRates.SHOP_ZONE).toLocalDate().minusDays(1);
	}

	/**
	 * Sends the report of {@code day} to the flow now, on request of a person. Unlike the nightly run this reports
	 * why it did not work: no URL configured, a day in the future, a request too soon after the previous one, or a flow
	 * that did not accept it. Returns what was sent.
	 */
	public DailySalesReport sendOnRequest(LocalDate day) {
		if (!flow.configured()) {
			throw new DailyReportException(DailyReportException.Reason.NOT_CONFIGURED,
					"The daily report is not set up: there is no URL for it (eurocart.daily-report.url)");
		}
		Instant now = clock.instant();
		if (day.isAfter(now.atZone(ExchangeRates.SHOP_ZONE).toLocalDate())) {
			throw new IllegalArgumentException("A day in the future has no report yet");
		}
		Instant previous = lastRequested.getAndSet(now);
		if (previous != null && Duration.between(previous, now).compareTo(MINIMUM_TIME_BETWEEN_REQUESTS) < 0) {
			lastRequested.set(previous);
			throw new DailyReportException(DailyReportException.Reason.TOO_SOON,
					"A report was sent a moment ago: wait " + MINIMUM_TIME_BETWEEN_REQUESTS.toSeconds() + " seconds");
		}
		DailySalesReport report = reports.reportFor(day);
		if (!flow.post(report, "Daily sales report of " + day + " (on request)")) {
			throw new DailyReportException(DailyReportException.Reason.NOT_DELIVERED,
					"The sales flow did not accept the report: see the log of the application");
		}
		return report;
	}

	/** Sends the report of {@code day} now. Never throws; returns whether the flow accepted it. */
	boolean send(LocalDate day) {
		if (!flow.configured()) {
			return false;
		}
		try {
			return flow.post(reports.reportFor(day), "Daily sales report of " + day);
		}
		catch (RuntimeException ex) {
			log.warn("Daily sales report of {} could not be made: {}", day, ex.getClass().getSimpleName());
			return false;
		}
	}

}
