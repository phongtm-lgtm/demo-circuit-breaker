package fu.ats.client.candidate;

import fu.ats.dto.CandidateView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "candidate-service", url = "${candidate-service.url}", configuration = CandidateFeignConfig.class)
public interface CandidateClient {

    @GetMapping("/api/v1/candidates/{id}")
    CandidateView getCandidateById(@PathVariable("id") Long id);
}
