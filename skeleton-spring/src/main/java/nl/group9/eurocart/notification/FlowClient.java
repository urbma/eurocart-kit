package nl.group9.eurocart.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * Posts JSON to a Power Automate flow. The URL of such a flow contains a secret ({@code sig=...}): it is only
 * accepted as https (or http to this very machine, for tests) and it is never logged. Posting never throws; a failure
 * is only logged and reported as {@code false}. Without a URL there is nothing to post to.
 */
public class FlowClient {

	private static final Logger log = LoggerFactory.getLogger(FlowClient.class);

	private final URI url;

	private final ObjectMapper objectMapper;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	/** {@code property} is only used to name the setting in the error message when the URL is not valid. */
	public FlowClient(String url, String property, ObjectMapper objectMapper) {
		this.url = parse(url, property);
		this.objectMapper = objectMapper;
	}

	public boolean configured() {
		return url != null;
	}

	/** Posts {@code payload} as JSON; {@code what} names the notification in the log. Returns whether the flow accepted it. */
	public boolean post(Object payload, String what) {
		if (url == null) {
			return false;
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(url)
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
					.build();
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() / 100 != 2) {
				log.warn("{} was refused: HTTP {}", what, response.statusCode());
				return false;
			}
			log.info("{} sent: HTTP {}", what, response.statusCode());
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("{} was interrupted", what);
			return false;
		}
		catch (IOException | RuntimeException ex) {
			log.warn("{} failed: {}", what, ex.getClass().getSimpleName());
			return false;
		}
	}

	/** Without echoing the value: it holds a secret. */
	private static URI parse(String url, String property) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(url.trim());
			boolean secure = "https".equals(uri.getScheme());
			boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
			if (!secure && !local) {
				throw new IllegalArgumentException();
			}
			return uri;
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(property + " is not a valid https URL");
		}
	}

}
