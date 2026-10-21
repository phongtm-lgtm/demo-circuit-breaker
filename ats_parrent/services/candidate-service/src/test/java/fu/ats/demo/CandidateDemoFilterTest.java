package fu.ats.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateDemoFilterTest {
    @Test
    void simulatesOutageAndRecoveryOnlyForCandidateReadRequests() throws Exception {
        var behavior = new CandidateDemoBehavior();
        var filter = new CandidateDemoFilter(behavior);
        var forwarded = new AtomicBoolean();
        var request = new MockHttpServletRequest("GET", "/api/v1/candidates/123");
        request.setServletPath("/api/v1/candidates/123");
        behavior.configure(new CandidateDemoBehavior.Settings(CandidateDemoBehavior.Mode.ERROR, 0));
        var failure = new MockHttpServletResponse();
        filter.doFilter(request, failure, (req, res) -> forwarded.set(true));
        assertThat(failure.getStatus()).isEqualTo(503);
        assertThat(forwarded).isFalse();
        assertThat(behavior.receivedCalls()).isEqualTo(1);

        behavior.configure(new CandidateDemoBehavior.Settings(CandidateDemoBehavior.Mode.NORMAL, 0));
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> forwarded.set(true));
        assertThat(forwarded).isTrue();
        assertThat(behavior.receivedCalls()).isEqualTo(2);

        behavior.configure(new CandidateDemoBehavior.Settings(CandidateDemoBehavior.Mode.ERROR, 0));
        var control = new MockHttpServletRequest("PUT", "/api/v1/services/candidate-service/behavior");
        control.setServletPath("/api/v1/services/candidate-service/behavior");
        forwarded.set(false);
        filter.doFilter(control, new MockHttpServletResponse(), (req, res) -> forwarded.set(true));
        assertThat(forwarded).isTrue();
        assertThat(behavior.receivedCalls()).isEqualTo(2);
    }
}
