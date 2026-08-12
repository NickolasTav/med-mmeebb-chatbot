package br.edu.unipam.tcc.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationMdcHelperTest {

    @AfterEach
    void tearDown() {
        CorrelationMdcHelper.clearContext();
    }

    @Test
    @DisplayName("Deve injetar studentPhone, messageId, action e gerar traceId no MDC se ausente")
    void shouldSetAndClearMdcContext() {
        CorrelationMdcHelper.setContext("5534999998888", "MSG_ABC_123", "PROCESS_REVIEW");

        assertEquals("5534999998888", MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
        assertEquals("MSG_ABC_123", MDC.get(CorrelationMdcHelper.MDC_MESSAGE_ID));
        assertEquals("PROCESS_REVIEW", MDC.get(CorrelationMdcHelper.MDC_ACTION));
        assertNotNull(MDC.get(CorrelationMdcHelper.MDC_TRACE_ID), "Trace ID deve ser gerado automaticamente no MDC se ausente");
        assertFalse(MDC.get(CorrelationMdcHelper.MDC_TRACE_ID).isBlank());

        CorrelationMdcHelper.clearContext();

        assertNull(MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
        assertNull(MDC.get(CorrelationMdcHelper.MDC_MESSAGE_ID));
        assertNull(MDC.get(CorrelationMdcHelper.MDC_ACTION));
        assertNull(MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("Deve preservar traceId existente gerado pelo Tracer/Brave no MDC")
    void shouldPreserveExistingTraceIdIfAlreadyPresent() {
        MDC.put(CorrelationMdcHelper.MDC_TRACE_ID, "brave-trace-id-12345");

        CorrelationMdcHelper.setContext("5534999998888", "MSG_ABC_123", "PROCESS_REVIEW");

        assertEquals("brave-trace-id-12345", MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
        assertEquals("5534999998888", MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
    }

    @Test
    @DisplayName("Deve permitir definição explícita de traceId customizado")
    void shouldAllowExplicitTraceId() {
        CorrelationMdcHelper.setContext("5534999998888", "MSG_ABC_123", "PROCESS_REVIEW", "custom-trace-999");

        assertEquals("custom-trace-999", MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
        assertEquals("5534999998888", MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
    }

    @Test
    @DisplayName("Deve garantir geração de traceId via ensureTraceId")
    void shouldEnsureTraceIdGeneratesValidId() {
        assertNull(MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
        String traceId = CorrelationMdcHelper.ensureTraceId();

        assertNotNull(traceId);
        assertEquals(traceId, MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
    }
}

