package fu.ats.dto;

import fu.ats.entity.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationDetailResponse(
        Long applicationId,
        UUID jobId,
        Long candidateId,
        ApplicationStatus status,
        String cvUrl,
        OffsetDateTime appliedAt,
        CandidateView candidate,
        boolean candidateInfoAvailable,
        String message
) {
}
