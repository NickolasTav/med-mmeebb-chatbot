package br.edu.unipam.tcc;

import br.edu.unipam.tcc.config.CorrelationMdcHelper;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TracingIntegrationTest {

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("Deve inicializar o Tracer do Micrometer Tracing para geração de Trace ID e Span ID")
    void tracerShouldBePresent() {
        assertNotNull(tracer, "O Bean de Tracer do Micrometer Tracing deve estar configurado no Spring Boot Context");
        assertNotNull(tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : tracer.nextSpan().context().traceId());
    }

    @Test
    @DisplayName("Deve garantir que o CorrelationMdcHelper gera e propaga traceId no MDC durante execução")
    void shouldPropagateTraceIdInMdc() {
        CorrelationMdcHelper.setContext("5534999991111", "MSG_TEST", "PROCESS_TEST");
        try {
            String traceId = MDC.get(CorrelationMdcHelper.MDC_TRACE_ID);
            assertNotNull(traceId, "O traceId não deve ser nulo no MDC");
            assertFalse(traceId.isBlank(), "O traceId não deve estar vazio");
            assertEquals("5534999991111", MDC.get(CorrelationMdcHelper.MDC_STUDENT_PHONE));
        } finally {
            CorrelationMdcHelper.clearContext();
        }
        assertNull(MDC.get(CorrelationMdcHelper.MDC_TRACE_ID));
    }
}

