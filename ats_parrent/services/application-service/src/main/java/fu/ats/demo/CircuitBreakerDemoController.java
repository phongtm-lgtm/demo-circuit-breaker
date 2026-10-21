package fu.ats.demo;

import fu.ats.client.candidate.CandidateDetailsGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("cb-demo")
@RestController
@RequestMapping("/api/v1/circuit-breakers/candidateDetails")
public class CircuitBreakerDemoController {
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerDemoController(CircuitBreakerRegistry registry) {
        circuitBreaker = registry.circuitBreaker(CandidateDetailsGateway.CIRCUIT_NAME);
    }

    @GetMapping
    public Snapshot status() {
        var metrics = circuitBreaker.getMetrics();
        return new Snapshot(circuitBreaker.getState(), metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfSuccessfulCalls(), metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(), metrics.getNumberOfNotPermittedCalls(),
                metrics.getFailureRate(), metrics.getSlowCallRate());
    }

    @PostMapping("/resets")
    public Snapshot reset() {
        circuitBreaker.reset();
        return status();
    }

    public record Snapshot(CircuitBreaker.State state, int bufferedCalls, int successfulCalls,
                           int failedCalls, int slowCalls, long notPermittedCalls,
                           float failureRate, float slowCallRate) {
    }
}
