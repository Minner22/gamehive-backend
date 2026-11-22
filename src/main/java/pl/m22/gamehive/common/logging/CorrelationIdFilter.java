package pl.m22.gamehive.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final int MAX_CORRELATION_ID_LENGTH = 64;

    static final String CORRELATION_ID_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_CORRELATION_ID_LENGTH + "}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        String correlationId = (request.getAttribute(CORRELATION_ID_ATTRIBUTE) instanceof String existing)
                ? existing
                : resolveCorrelationId(request.getHeader(CORRELATION_ID_HEADER));

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);

        MDC.put(CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    static String resolveCorrelationId(String header) {

        if (header == null) {
            return UUID.randomUUID().toString();
        }

        if (VALID_CORRELATION_ID.matcher(header).matches()) {
            return header;
        }

        log.debug("Header {} (length {}) discarded. Generating a new one.", CORRELATION_ID_HEADER, header.length());

        return UUID.randomUUID().toString();
    }
}
