package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.service.impl.AppMetricsServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private AppMetricsService appMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        appMetricsService = new AppMetricsServiceImpl(meterRegistry);
    }

    @Test
    @DisplayName("Deve incrementar contador de revisões com tags corretas")
    void shouldIncrementReviewCounter() {
        appMetricsService.recordReviewAnswer(true, "PEDIATRIA");
        appMetricsService.recordReviewAnswer(false, "PEDIATRIA");
        appMetricsService.recordReviewAnswer(true, "CLINICA_MEDICA");

        double correctPediatria = meterRegistry.get("med_mmeebb_reviews_total")
                .tag("result", "correct")
                .tag("specialty", "PEDIATRIA")
                .counter().count();

        double incorrectPediatria = meterRegistry.get("med_mmeebb_reviews_total")
                .tag("result", "incorrect")
                .tag("specialty", "PEDIATRIA")
                .counter().count();

        assertEquals(1.0, correctPediatria);
        assertEquals(1.0, incorrectPediatria);
    }

    @Test
    @DisplayName("Deve incrementar contador de IA com fallback")
    void shouldIncrementAiInteractions() {
        appMetricsService.recordAiInteraction("INTENT_CLASSIFICATION", false);
        appMetricsService.recordAiInteraction("CLINICAL_TUTOR", true);

        double successCount = meterRegistry.get("med_mmeebb_ai_interactions_total")
                .tag("type", "INTENT_CLASSIFICATION")
                .tag("fallback", "false")
                .counter().count();

        double fallbackCount = meterRegistry.get("med_mmeebb_ai_interactions_total")
                .tag("type", "CLINICAL_TUTOR")
                .tag("fallback", "true")
                .counter().count();

        assertEquals(1.0, successCount);
        assertEquals(1.0, fallbackCount);
    }

    @Test
    @DisplayName("Deve incrementar contador de mensagens WhatsApp")
    void shouldIncrementWhatsAppMessages() {
        appMetricsService.recordWhatsAppMessage("INBOUND", "TEXT");
        appMetricsService.recordWhatsAppMessage("OUTBOUND", "QUESTION");

        double inboundCount = meterRegistry.get("med_mmeebb_uaizap_messages_total")
                .tag("direction", "INBOUND")
                .tag("type", "TEXT")
                .counter().count();

        assertEquals(1.0, inboundCount);
    }
}
