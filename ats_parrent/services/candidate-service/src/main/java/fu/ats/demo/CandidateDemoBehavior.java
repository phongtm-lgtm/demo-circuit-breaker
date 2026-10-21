package fu.ats.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("cb-demo")
public class CandidateDemoBehavior {
    private volatile Settings settings = new Settings(Mode.NORMAL, 3000);
    private final AtomicLong receivedCalls = new AtomicLong();

    public Settings settings() {
        return settings;
    }

    public void configure(Settings settings) {
        this.settings = settings;
    }

    public long recordCall() {
        return receivedCalls.incrementAndGet();
    }

    public long receivedCalls() {
        return receivedCalls.get();
    }

    public enum Mode { NORMAL, ERROR, SLOW, NOT_FOUND }

    public record Settings(Mode mode, long delayMs) {
    }
}
