package nl.group9.eurocart;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * The shop with MockMvc, a fixed clock and no real notifications to the sales person: the URLs of the flows are emptied,
 * because a local config/application.properties may hold the real ones, and a recording notifier takes the place of the
 * real one.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"eurocart.sales-notification.url=", "eurocart.daily-report.url="})
@AutoConfigureMockMvc
@Import({TestClockConfig.class, TestSalesNotifierConfig.class})
public @interface ShopTest {
}
