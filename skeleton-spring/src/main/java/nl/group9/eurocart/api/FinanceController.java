package nl.group9.eurocart.api;

import nl.group9.eurocart.finance.FinanceOverview;
import nl.group9.eurocart.finance.FinanceService;

import java.math.BigDecimal;
import java.time.LocalDate;

import nl.group9.eurocart.report.DailySalesReport;
import nl.group9.eurocart.report.DailySalesReportJob;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

	private final FinanceService finance;

	private final DailySalesReportJob dailyReport;

	public FinanceController(FinanceService finance, DailySalesReportJob dailyReport) {
		this.finance = finance;
		this.dailyReport = dailyReport;
	}

	@GetMapping("/orders")
	public FinanceOverview orders() {
		return finance.overview();
	}

	/**
	 * Sends the daily report of one day to the sales person now, like the nightly run does. Without a date it is
	 * the report of yesterday, which is what the nightly run sends. The shop has no login yet, so this can be
	 * triggered by anyone who can reach it; that is why requests need to be a few seconds apart.
	 */
	@PostMapping("/daily-report")
	public SentReport sendDailyReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		LocalDate day = date != null ? date : dailyReport.yesterday();
		DailySalesReport report = dailyReport.sendOnRequest(day);
		return new SentReport(day, report.totaalOrders(), report.totaalOmzetInEuro());
	}

	public record SentReport(LocalDate date, int totaalOrders, BigDecimal totaalOmzetInEuro) {
	}

}
