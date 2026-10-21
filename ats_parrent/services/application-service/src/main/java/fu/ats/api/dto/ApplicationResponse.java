package fu.ats.api.dto;

import fu.ats.entity.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationResponse(
        Long id,
        UUID jobId,
        Long candidateId,
        String cvUrl,
        ApplicationStatus status,
        Long pipelineStageId,
        OffsetDateTime appliedAt
) {
}
