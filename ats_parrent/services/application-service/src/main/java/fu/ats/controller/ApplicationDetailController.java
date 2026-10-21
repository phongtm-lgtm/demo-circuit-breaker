package fu.ats.controller;

import fu.ats.dto.ApplicationDetailResponse;
import fu.ats.service.ApplicationDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationDetailController {
    private final ApplicationDetailService applicationDetailService;

    @GetMapping("/{id}")
    public ApplicationDetailResponse detail(@PathVariable("id") Long id,
                                            @RequestHeader("X-User-ID") String userId,
                                            @RequestHeader("X-User-Email") String email,
                                            @RequestHeader("X-User-Name") String userName) {
        return applicationDetailService.findById(id);
    }
}
