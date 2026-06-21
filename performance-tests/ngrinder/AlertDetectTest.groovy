import static net.grinder.script.Grinder.grinder
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

import net.grinder.plugin.http.HTTPRequest
import net.grinder.plugin.http.HTTPResponse
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GrinderRunner)
class AlertDetectTest {
    public static GTest test
    public static HTTPRequest request

    private static final String BASE_URL = System.getProperty("baseUrl", "http://host.docker.internal:8083")
    private static final String[] STOCKS = ["005930", "000660", "035420", "035720", "005380"]

    @BeforeProcess
    public static void beforeProcess() {
        test = new GTest(1, "alert detect")
        request = new HTTPRequest()
        test.record(request)
        grinder.logger.info("Target baseUrl: {}", BASE_URL)
    }

    @BeforeThread
    public void beforeThread() {
        grinder.statistics.delayReports = true
    }

    @Test
    public void alertDetect() {
        int index = Math.abs(grinder.threadNumber + grinder.runNumber) % STOCKS.length
        String stockCode = STOCKS[index]
        String url = "${BASE_URL}/load-test/alerts/detect?stockCode=${stockCode}"

        HTTPResponse response = request.POST(url, new byte[0])
        assertThat(response.statusCode, is(200))
    }
}
