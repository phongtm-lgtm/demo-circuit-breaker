package fu.ats.service;

import fu.ats.client.candidate.CandidateClient;
import fu.ats.client.job.JobClient;
import fu.ats.dto.ApplicationRequest;
import fu.ats.dto.CandidateView;
import fu.ats.dto.JobView;
import fu.ats.entity.Application;
import fu.ats.entity.ApplicationStatus;
import fu.ats.entity.PipelineStage;
import fu.ats.exception.BusinessException;
import fu.ats.repository.ApplicationRepository;
import fu.ats.repository.PipelineStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationServiceTest {
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final PipelineStageRepository stages = mock(PipelineStageRepository.class);
    private final JobClient jobs = mock(JobClient.class);
    private final CandidateClient candidates = mock(CandidateClient.class);
    private final ApplicationService service = new ApplicationServiceImpl(applications, stages, jobs, candidates);
    private final ApplicationRequest request = new ApplicationRequest(
            UUID.randomUUID(), 42L, "https://example.com/cv.pdf");

    @BeforeEach
    void validJobAndCandidate() {
        when(jobs.findById(request.jobId())).thenReturn(JobView.builder()
                .id(request.jobId()).status("PUBLISHED").deadline(OffsetDateTime.now().plusDays(1)).build());
        when(candidates.getCandidateById(42L)).thenReturn(CandidateView.builder().id(42L).status("ACTIVE").build());
    }

    @Test
    void createsSubmittedApplicationWithDefaultStageAndCvUrl() {
        PipelineStage stage = new PipelineStage();
        stage.setId(1L);
        when(stages.findByDefaultStage(true)).thenReturn(stage);
        when(applications.save(any(Application.class))).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            assertEquals(request.jobId(), application.getJobId());
            assertEquals(request.candidateId(), application.getCandidateId());
            assertEquals(request.cvUrl(), application.getCvUrl());
            assertEquals(ApplicationStatus.SUBMITTED, application.getStatus());
            assertSame(stage, application.getPipelineStage());
            assertNotNull(application.getAppliedAt());
            application.setId(101L);
            return application;
        });
        var response = service.create(request);
        assertEquals(101L, response.id());
        assertEquals("SUBMITTED", response.status());
        verify(applications).save(any(Application.class));
    }

    @Test
    void rejectsUnpublishedJobBeforeCheckingCandidateOrSaving() {
        when(jobs.findById(request.jobId())).thenReturn(JobView.builder().status("DRAFT").build());
        assertThrows(BusinessException.class, () -> service.create(request));
        verifyNoInteractions(candidates, applications, stages);
    }

    @Test
    void rejectsDuplicateApplicationWithoutSaving() {
        when(applications.existsByJobIdAndCandidateIdAndStatusNot(
                request.jobId(), request.candidateId(), ApplicationStatus.WITHDRAWN)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.create(request));
        verify(applications, never()).save(any());
        verifyNoInteractions(stages);
    }
}
