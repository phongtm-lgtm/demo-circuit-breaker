package fu.ats.service;

import fu.ats.client.candidate.CandidateDetailsGateway;
import fu.ats.dto.ApplicationDetailResponse;
import fu.ats.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ApplicationDetailService {
    private final ApplicationRepository applicationRepository;
    private final CandidateDetailsGateway candidateDetailsGateway;

    public ApplicationDetailResponse findById(Long id) {
        var application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        var result = candidateDetailsGateway.findById(application.getCandidateId());
        return new ApplicationDetailResponse(application.getId(), application.getJobId(),
                application.getCandidateId(), application.getStatus(), application.getCvUrl(),
                application.getAppliedAt(), result.candidate(), result.candidate() != null, result.message());
    }
}
