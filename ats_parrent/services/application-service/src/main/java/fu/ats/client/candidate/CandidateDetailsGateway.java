package fu.ats.client.candidate;

import feign.FeignException;
import feign.RetryableException;
import fu.ats.dto.CandidateView;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CandidateDetailsGateway {
    public static final String CIRCUIT_NAME = "candidateDetails";
    private static final Logger log = LoggerFactory.getLogger(CandidateDetailsGateway.class);
    private final CandidateClient candidateClient;
    private final CircuitBreaker circuitBreaker;

    public CandidateDetailsGateway(CandidateClient candidateClient, CircuitBreakerRegistry registry) {
        this.candidateClient = candidateClient;
        this.circuitBreaker = registry.circuitBreaker(CIRCUIT_NAME);
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.info("Circuit {}: {}", CIRCUIT_NAME, event.getStateTransition()));
    }

    public LookupResult findById(Long candidateId) {
        try {
            // Record only the remote call, before applying fallback.
            var candidate = circuitBreaker.executeSupplier(() -> candidateClient.getCandidateById(candidateId));
            return new LookupResult(candidate, null);
        } catch (FeignException.NotFound error) {
            return new LookupResult(null, "Ứng viên không còn tồn tại.");
        } catch (CallNotPermittedException | RetryableException error) {
            return unavailable(candidateId, error);
        } catch (FeignException error) {
            if (error.status() >= 500) {
                return unavailable(candidateId, error);
            }
            if (error.status() >= 400) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(error.status()),
                        "Candidate-service từ chối yêu cầu.", error);
            }
            throw error;
        }
    }

    private LookupResult unavailable(Long candidateId, RuntimeException error) {
        log.warn("Candidate details unavailable: candidateId={}, circuit={}, cause={}",
                candidateId, circuitBreaker.getState(), error.getClass().getSimpleName());
        return new LookupResult(null, "Thông tin ứng viên tạm thời không khả dụng.");
    }

    public record LookupResult(CandidateView candidate, String message) {
    }
}
