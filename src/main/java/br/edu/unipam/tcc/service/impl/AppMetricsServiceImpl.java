package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.service.AppMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class AppMetricsServiceImpl implements AppMetricsService {

    private final MeterRegistry meterRegistry;

    public AppMetricsServiceImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordReviewAnswer(boolean isCorrect, String specialty) {
        Counter.builder("med_mmeebb_reviews_total")
                .description("Total de respostas de revisões espaçadas computadas no algoritmo MMEEBB")
                .tag("result", isCorrect ? "correct" : "incorrect")
                .tag("specialty", specialty != null ? specialty : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordAiInteraction(String interactionType, boolean isFallback) {
        Counter.builder("med_mmeebb_ai_interactions_total")
                .description("Total de chamadas e interações com IA / Classificador Semântico")
                .tag("type", interactionType != null ? interactionType : "GENERAL")
                .tag("fallback", String.valueOf(isFallback))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordWhatsAppMessage(String direction, String messageType) {
        Counter.builder("med_mmeebb_uaizap_messages_total")
                .description("Total de mensagens trafegadas no gateway UaiZap (WhatsApp)")
                .tag("direction", direction != null ? direction : "UNKNOWN")
                .tag("type", messageType != null ? messageType : "TEXT")
                .register(meterRegistry)
                .increment();
    }
}
