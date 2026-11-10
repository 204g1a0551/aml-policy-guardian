package com.bank.aml.unit;

import com.bank.aml.security.correlation.CorrelationIdContext;
import com.bank.aml.security.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdMdcUnitTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        CorrelationIdContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("CorrelationId: Generates UUID when none provided in request headers")
    void testGeneratesUuidWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotNull();
        assertThat(responseHeader).hasSize(36); // standard UUID format
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // cleaned up in finally
    }

    @Test
    @DisplayName("CorrelationId: Propagates and sanitizes client-supplied X-Correlation-ID")
    void testPropagatesAndSanitizesHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-req-12345; injection attack<script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isEqualTo("corr-req-12345injectionattackscript");
    }

    @Test
    @DisplayName("CorrelationId: Context thread-local lifecycle")
    void testContextLifecycle() {
        CorrelationIdContext.setCorrelationId("test-trace-abc");
        assertThat(CorrelationIdContext.getCorrelationId()).isEqualTo("test-trace-abc");

        CorrelationIdContext.clear();
        // Once cleared, getting correlation ID generates a fresh fallback
        assertThat(CorrelationIdContext.getCorrelationId()).isNotEqualTo("test-trace-abc");
    }
}
