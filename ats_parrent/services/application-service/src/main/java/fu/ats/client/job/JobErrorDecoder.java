package fu.ats.client.job;

import feign.Response;
import feign.codec.ErrorDecoder;
import fu.ats.exception.DownStreamUnavailableException;
import fu.ats.exception.JobNotFoundException;
import fu.ats.exception.JobRequestRejectedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class JobErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder delegate = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {

        if (response.status() == 404) {
            String message = "Job not found";
            if (response.body() != null) {
                try (var body = response.body().asInputStream()) {
                    message = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // Preserve the 404 classification even if its optional body cannot be read.
                }
            }
            return new JobNotFoundException("JOB_NOT_FOUND", message, null, Instant.now());
        }
        if (response.status() >= 400 && response.status() < 500) {
            return new JobRequestRejectedException(response.status(), response.reason());
        }
        if (response.status() == 500 || response.status() == 503 || response.status() == 504) {
            return new DownStreamUnavailableException("job-service", response.reason());
        }
        return delegate.decode(methodKey, response);
    }
}
