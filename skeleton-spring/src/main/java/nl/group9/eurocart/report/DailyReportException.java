package nl.group9.eurocart.report;

/** The daily report could not be sent on request. {@link #reason()} says why, the message is meant for the person who asked. */
public class DailyReportException extends RuntimeException {

	public enum Reason {
		/** There is no URL for the flow of the daily report. */
		NOT_CONFIGURED,
		/** The flow did not accept the report. */
		NOT_DELIVERED,
		/** A report was sent on request a moment ago. */
		TOO_SOON
	}

	private final Reason reason;

	public DailyReportException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
