package pl.m22.gamehive.common.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final int MAX_CORRELATION_ID_LENGTH = 64;

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_CORRELATION_ID_LENGTH + "}");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String correlationId = resolveCorrelationId(request.getHeader(CORRELATION_ID_HEADER));

        MDC.put(CORRELATION_ID, correlationId);
        ((HttpServletResponse) servletResponse).setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            MDC.remove(CORRELATION_ID);
        }
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
