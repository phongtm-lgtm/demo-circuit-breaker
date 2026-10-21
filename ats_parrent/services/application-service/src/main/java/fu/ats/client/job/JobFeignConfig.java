package fu.ats.client.job;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

public class JobFeignConfig {

    @Bean
    public ErrorDecoder defaultErrorDecoder() {
        return new JobErrorDecoder();
    }

    @Bean
    public Retryer jobRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor jobUserHeaders() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                for (String header : List.of("X-User-ID", "X-User-Email", "X-User-Name")) {
                    String value = attributes.getRequest().getHeader(header);
                    if (value != null) {
                        template.header(header, value);
                    }
                }
            }
        };
    }
}
