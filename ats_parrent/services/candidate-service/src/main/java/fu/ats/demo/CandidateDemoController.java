package fu.ats.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("cb-demo")
@RestController
@RequestMapping("/api/v1/services/candidate-service/behavior")
@RequiredArgsConstructor
public class CandidateDemoController {
    private final CandidateDemoBehavior behavior;

    @GetMapping
    public Snapshot status() {
        return new Snapshot(behavior.settings(), behavior.receivedCalls());
    }

    @PutMapping
    public Snapshot configure(@Valid @RequestBody ConfigureRequest request) {
        behavior.configure(new CandidateDemoBehavior.Settings(request.mode(), request.delayMs()));
        return status();
    }

    public record ConfigureRequest(@NotNull CandidateDemoBehavior.Mode mode,
                                   @Min(0) @Max(30000) long delayMs) {
    }

    public record Snapshot(CandidateDemoBehavior.Settings settings, long receivedCalls) {
    }
}
