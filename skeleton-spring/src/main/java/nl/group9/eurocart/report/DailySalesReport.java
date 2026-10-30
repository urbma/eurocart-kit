package nl.group9.eurocart.report;

import java.math.BigDecimal;

/** What the sales person gets once a day. The names are the fields of the flow: they are sent as they are. */
public record DailySalesReport(int totaalOrders, BigDecimal totaalOmzetInEuro) {
}
