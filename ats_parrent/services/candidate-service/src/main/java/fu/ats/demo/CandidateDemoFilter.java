package fu.ats.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Profile("cb-demo")
@RequiredArgsConstructor
public class CandidateDemoFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CandidateDemoFilter.class);
    private final CandidateDemoBehavior behavior;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || !request.getServletPath().matches("/api/v1/candidates/[^/]+");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var settings = behavior.settings();
        log.info("Demo candidate call #{}: mode={}, path={}",
                behavior.recordCall(), settings.mode(), request.getServletPath());
        switch (settings.mode()) {
            case ERROR -> {
                respond(response, 503, "Simulated candidate-service outage");
                return;
            }
            case NOT_FOUND -> {
                respond(response, 404, "Candidate not found (demo)");
                return;
            }
            case SLOW -> {
                try {
                    Thread.sleep(settings.delayMs());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    respond(response, 503, "Demo delay interrupted");
                    return;
                }
            }
            case NORMAL -> { }
        }
        chain.doFilter(request, response);
    }

    private void respond(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain");
        response.getWriter().write(message);
    }
}
