package fu.ats.client.candidate;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

public class CandidateFeignConfig {
    @Bean
    public Retryer candidateRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
