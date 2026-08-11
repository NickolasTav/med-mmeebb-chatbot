package br.edu.unipam.tcc.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorrelationMdcHelperTest {

    @AfterEach
    void tearDown() {
        CorrelationMdcHelper.clearContext();
    }

    @Test
    @DisplayName("Deve injetar studentPhone, messageId e action no MDC e limpar corretamente")
    void shouldSetAndClearMdcContext() {
        CorrelationMdcHelper.setContext("5534999998888", "MSG_ABC_123", "PROCESS_REVIEW");

        assertEquals("5534999998888", MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
        assertEquals("MSG_ABC_123", MDC.get(CorrelationMdcHelper.MDC_MESSAGE_ID));
        assertEquals("PROCESS_REVIEW", MDC.get(CorrelationMdcHelper.MDC_ACTION));

        CorrelationMdcHelper.clearContext();

        assertNull(MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
        assertNull(MDC.get(CorrelationMdcHelper.MDC_MESSAGE_ID));
        assertNull(MDC.get(CorrelationMdcHelper.MDC_ACTION));
    }
}
