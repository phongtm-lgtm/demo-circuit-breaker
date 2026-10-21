package fu.ats.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID jobId,
        @NotNull @Positive Long candidateId,
        @NotBlank @Size(max = 1000)
        @URL
        @Pattern(regexp = "https?://[^\\s]+", message = "must be an HTTP or HTTPS URL")
        String cvUrl
) {
}
