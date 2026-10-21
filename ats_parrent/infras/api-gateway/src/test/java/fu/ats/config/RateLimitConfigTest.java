package fu.ats.config;

import fu.ats.filters.Constants;
import fu.ats.filters.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitConfigTest {
    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    void resolvesPeerIpAndIgnoresSpoofedForwardingHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-For", "203.0.113.10"));
        StepVerifier.create(config.clientIpKeyResolver().resolve(exchange))
                .expectNext("127.0.0.1").verifyComplete();
    }

    @Test
    void missingPeerAddressReturnsNoKey() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs"));
        StepVerifier.create(config.clientIpKeyResolver().resolve(exchange)).verifyComplete();
    }

    @Test
    void builtInFilterRejectsOverLimitWith429AndCorrelationId() {
        RateLimiter<?> limiter = mock(RateLimiter.class);
        when(limiter.isAllowed("job-service", "127.0.0.1"))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of("X-RateLimit-Remaining", "0"))));
        var factory = new RequestRateLimiterGatewayFilterFactory(limiter, config.clientIpKeyResolver());
        var settings = new RequestRateLimiterGatewayFilterFactory.Config();
        settings.setRouteId("job-service");
        var rateLimitFilter = factory.apply(settings);
        GatewayFilterChain downstream = mock(GatewayFilterChain.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345)));

        StepVerifier.create(new CorrelationIdFilter().filter(exchange,
                forwarded -> rateLimitFilter.filter(forwarded, downstream))).verifyComplete();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("0", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        assertNotNull(exchange.getResponse().getHeaders().getFirst(Constants.CORRELATION_ID));
        verifyNoInteractions(downstream);
    }

    @Test
    void builtInFilterAllowsRequestWithinLimit() {
        RateLimiter<?> limiter = mock(RateLimiter.class);
        when(limiter.isAllowed("candidate-service", "127.0.0.1"))
                .thenReturn(Mono.just(new RateLimiter.Response(true, Map.of())));
        var factory = new RequestRateLimiterGatewayFilterFactory(limiter, config.clientIpKeyResolver());
        var settings = new RequestRateLimiterGatewayFilterFactory.Config();
        settings.setRouteId("candidate-service");
        GatewayFilterChain downstream = mock(GatewayFilterChain.class);
        when(downstream.filter(any())).thenReturn(Mono.empty());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/candidates")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345)));

        StepVerifier.create(factory.apply(settings).filter(exchange, downstream)).verifyComplete();
        verify(downstream).filter(exchange);
    }

    @Test
    void builtInFilterDeniesEmptyKeyWithoutCallingRedis() {
        RateLimiter<?> limiter = mock(RateLimiter.class);
        var factory = new RequestRateLimiterGatewayFilterFactory(limiter, config.clientIpKeyResolver());
        GatewayFilterChain downstream = mock(GatewayFilterChain.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs"));

        StepVerifier.create(factory.apply(new RequestRateLimiterGatewayFilterFactory.Config())
                .filter(exchange, downstream)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(limiter, downstream);
    }
}
