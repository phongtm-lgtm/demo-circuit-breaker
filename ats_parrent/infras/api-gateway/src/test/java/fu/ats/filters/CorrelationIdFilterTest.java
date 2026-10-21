package fu.ats.filters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesIdAndMakesItAvailableDownstreamAndInContext() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs"));
        AtomicReference<String> id = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, forwarded -> Mono.deferContextual(context -> {
            id.set(forwarded.getRequest().getHeaders().getFirst(Constants.CORRELATION_ID));
            assertEquals(id.get(), context.get(Constants.CORRELATION_ID));
            assertEquals(id.get(), forwarded.getAttribute(Constants.CORRELATION_ID));
            return forwarded.getResponse().setComplete();
        }))).verifyComplete();

        assertNotNull(UUID.fromString(id.get()));
        assertEquals(id.get(), exchange.getResponse().getHeaders().getFirst(Constants.CORRELATION_ID));
    }

    @Test
    void preservesValidIdOnRateLimitedResponseEvenIfDownstreamChangesHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .header(Constants.CORRELATION_ID, "client-request_123"));

        StepVerifier.create(filter.filter(exchange, forwarded -> {
            assertEquals("client-request_123", forwarded.getRequest().getHeaders().getFirst(Constants.CORRELATION_ID));
            forwarded.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            forwarded.getResponse().getHeaders().set(Constants.CORRELATION_ID, "downstream-id");
            return forwarded.getResponse().setComplete();
        })).verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("client-request_123", exchange.getResponse().getHeaders().getFirst(Constants.CORRELATION_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "invalid id", "<script>",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void replacesInvalidIds(String value) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .header(Constants.CORRELATION_ID, value));
        StepVerifier.create(filter.filter(exchange, forwarded -> {
            String id = forwarded.getRequest().getHeaders().getFirst(Constants.CORRELATION_ID);
            assertNotEquals(value, id);
            assertNotNull(UUID.fromString(id));
            return Mono.empty();
        })).verifyComplete();
    }

    @Test
    void replacesMultipleIdsWithOneId() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .header(Constants.CORRELATION_ID, "first", "second"));
        StepVerifier.create(filter.filter(exchange, forwarded -> {
            var ids = forwarded.getRequest().getHeaders().get(Constants.CORRELATION_ID);
            assertNotNull(ids);
            assertEquals(1, ids.size());
            assertNotNull(UUID.fromString(ids.getFirst()));
            return Mono.empty();
        })).verifyComplete();
    }

    @Test
    void runsBeforeLoggingAndRouteFilters() {
        assertTrue(filter.getOrder() < new LoggingFilter().getOrder());
        assertTrue(Constants.LOGGING_ORDER < 0);
    }
}
