package fu.ats.api.rest;

import fu.ats.dto.ApplicationResponse;
import fu.ats.dto.ApplicationRequest;
import fu.ats.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationControllerTest {
    private final ApplicationService service = mock(ApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationController(service))
                .defaultRequest(post("/").header("X-User-ID", "42")
                        .header("X-User-Email", "demo@example.com").header("X-User-Name", "Demo"))
                .build();
    }

    @Test
    void returns201WithCreatedApplication() throws Exception {
        UUID jobId = UUID.fromString("018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01");
        when(service.create(any())).thenReturn(new ApplicationResponse(101L, jobId, 42L,
                "https://example.com/cv.pdf", "SUBMITTED",
                OffsetDateTime.parse("2026-10-05T09:30:00Z")));

        mvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content("""
                {"jobId":"018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01","candidateId":42,"cvUrl":"https://example.com/cv.pdf"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.candidateId").value(42))
                .andExpect(jsonPath("$.cvUrl").value("https://example.com/cv.pdf"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(header().string("Location", "/api/v1/applications/101"));

        verify(service).create(new ApplicationRequest(jobId, 42L, "https://example.com/cv.pdf"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"jobId\":\"invalid\",\"candidateId\":42,\"cvUrl\":\"https://example.com/cv.pdf\"}",
            "{\"jobId\":\"018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01\",\"candidateId\":0,\"cvUrl\":\"https://example.com/cv.pdf\"}",
            "{\"jobId\":\"018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01\",\"candidateId\":42,\"cvUrl\":\"\"}",
            "{\"jobId\":\"018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01\",\"candidateId\":42,\"cvUrl\":\"ftp://example.com/cv.pdf\"}",
            "{\"jobId\":\"018f2f95-4f7f-7c4e-b2d7-2bba5a4a2f01\",\"candidateId\":42,\"cvUrl\":\"https://\"}"
    })
    void rejectsInvalidRequestWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
