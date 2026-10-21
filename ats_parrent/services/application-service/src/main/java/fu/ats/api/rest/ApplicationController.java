package fu.ats.api.rest;

import fu.ats.dto.ApplicationResponse;
import fu.ats.dto.ApplicationRequest;
import fu.ats.api.dto.CreateApplicationRequest;
import fu.ats.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Slf4j
@RequestMapping("/api/v1/applications")
public class ApplicationController {
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request,
                                                     @RequestHeader("X-User-ID") String userId,
                                                     @RequestHeader("X-User-Email") String email,
                                                     @RequestHeader("X-User-Name") String userName) {
        log.info("User Id {}, User email {}, name {}", userId, email, userName);
        ApplicationResponse response = applicationService.create(
                new ApplicationRequest(request.jobId(), request.candidateId(), request.cvUrl()));
        return ResponseEntity.created(URI.create("/api/v1/applications/" + response.id())).body(response);
    }
}
