package fu.ats.detail;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import fu.ats.client.candidate.CandidateClient;
import fu.ats.client.candidate.CandidateDetailsGateway;
import fu.ats.client.job.JobClient;
import fu.ats.controller.ApplicationDetailController;
import fu.ats.dto.CandidateView;
import fu.ats.entity.Application;
import fu.ats.entity.ApplicationStatus;
import fu.ats.repository.ApplicationRepository;
import fu.ats.service.ApplicationDetailService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationDetailCircuitBreakerTest {
    private final CandidateClient client = mock(CandidateClient.class);
    private final JobClient jobClient = mock(JobClient.class);
    private final ApplicationRepository repository = mock(ApplicationRepository.class);
    private final Request request = Request.create(Request.HttpMethod.GET, "http://localhost/candidates/42",
            Map.of(), null, StandardCharsets.UTF_8, null);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
            .withInitializer(ctx -> {
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                            .forEach(source -> ctx.getEnvironment().getPropertySources().addLast(source));
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            })
            .withBean(CandidateClient.class, () -> client)
            .withBean(JobClient.class, () -> jobClient)
            .withBean(ApplicationRepository.class, () -> repository)
            .withBean(CandidateDetailsGateway.class)
            .withBean(ApplicationDetailService.class);

    @Test
    void opensOnServerErrorsBlocksNextCallAndRecoversAfterTwoProbes() {
        when(client.getCandidateById(42L)).thenThrow(new FeignException.ServiceUnavailable("offline", request, null, Map.of()));
        context.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var gateway = ctx.getBean(CandidateDetailsGateway.class);
            var circuit = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("candidateDetails");
            for (int i = 0; i < 4; i++) {
                assertThat(gateway.findById(42L).candidate()).isNull();
            }
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            gateway.findById(42L);
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            gateway.findById(42L);
            verify(client, times(5)).getCandidateById(42L);
            assertThat(circuit.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
            doReturn(CandidateView.builder().id(42L).fullName("Demo Candidate").build()).when(client).getCandidateById(42L);
            circuit.transitionToHalfOpenState();
            assertThat(gateway.findById(42L).candidate().getFullName()).isEqualTo("Demo Candidate");
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
            gateway.findById(42L);
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            verifyNoInteractions(jobClient);
        });
    }

    @Test
    void ignores404ButRecordsConnectionFailures() {
        when(client.getCandidateById(42L)).thenThrow(new FeignException.NotFound("missing", request, null, Map.of()));
        context.run(ctx -> {
            var gateway = ctx.getBean(CandidateDetailsGateway.class);
            var circuit = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("candidateDetails");
            for (int i = 0; i < 6; i++) {
                assertThat(gateway.findById(42L).message()).contains("không còn tồn tại");
            }
            assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isZero();
            doThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request))
                    .when(client).getCandidateById(42L);
            for (int i = 0; i < 5; i++) {
                gateway.findById(42L);
            }
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        });
    }

    @Test
    void opensForSlowSuccesses() {
        context.run(ctx -> {
            var circuit = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("candidateDetails");
            for (int i = 0; i < 5; i++) {
                circuit.acquirePermission();
                circuit.onSuccess(3, TimeUnit.SECONDS);
            }
            assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
            assertThat(circuit.getMetrics().getSlowCallRate()).isEqualTo(100);
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        });
    }

    @Test
    void returnsCandidateOrPartialResponseAndNeverCallsJobService() {
        var application = new Application();
        application.setId(1L);
        application.setJobId(UUID.randomUUID());
        application.setCandidateId(42L);
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(repository.findById(999L)).thenReturn(Optional.empty());
        when(client.getCandidateById(42L)).thenReturn(CandidateView.builder().id(42L).fullName("Demo Candidate").build());
        context.run(ctx -> {
            var mvc = MockMvcBuilders.standaloneSetup(new ApplicationDetailController(ctx.getBean(ApplicationDetailService.class))).build();
            mvc.perform(get("/api/v1/applications/1").header("X-User-ID", "42")
                            .header("X-User-Email", "demo@example.com").header("X-User-Name", "Demo"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.candidateInfoAvailable").value(true))
                    .andExpect(jsonPath("$.candidate.fullName").value("Demo Candidate"));
            doThrow(new FeignException.BadGateway("bad gateway", request, null, Map.of())).when(client).getCandidateById(42L);
            mvc.perform(get("/api/v1/applications/1").header("X-User-ID", "42")
                            .header("X-User-Email", "demo@example.com").header("X-User-Name", "Demo"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.applicationId").value(1))
                    .andExpect(jsonPath("$.candidateInfoAvailable").value(false))
                    .andExpect(jsonPath("$.candidate").isEmpty());
            clearInvocations(client);
            mvc.perform(get("/api/v1/applications/999").header("X-User-ID", "42")
                            .header("X-User-Email", "demo@example.com").header("X-User-Name", "Demo"))
                    .andExpect(status().isNotFound());
            verifyNoInteractions(client, jobClient);
        });
    }
}
