package br.edu.unipam.tcc.controller;

import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UaiZapWebhookControllerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private UaiZapWebhookController controller;

    private final String exchange = "ex.med.direct";
    private final String routingKey = "rk.uaizap.inbound";

    @BeforeEach
    void setUp() {
        controller = new UaiZapWebhookController(rabbitTemplate, exchange, routingKey);
    }

    @Test
    @DisplayName("Deve enfileirar mensagens recebidas de alunos imediatamente no RabbitMQ")
    void shouldQueueIncomingWebhook() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event("messages.upsert")
                .data(new UaiZapWebhookPayload.MessageData(
                        new UaiZapWebhookPayload.MessageKey("5534999999999@s.whatsapp.net", false, "MSG_1"),
                        "Student",
                        "conversation",
                        new UaiZapWebhookPayload.MessageContent("A", null),
                        System.currentTimeMillis()
                ))
                .build();

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "QUEUED");
        verify(rabbitTemplate).convertAndSend(eq(exchange), eq(routingKey), eq(payload));
    }

    @Test
    @DisplayName("Deve ignorar mensagens enviadas pelo próprio bot (fromMe = true)")
    void shouldIgnoreFromMeMessages() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event("messages.upsert")
                .data(new UaiZapWebhookPayload.MessageData(
                        new UaiZapWebhookPayload.MessageKey("5534999999999@s.whatsapp.net", true, "MSG_BOT"),
                        "Bot",
                        "conversation",
                        new UaiZapWebhookPayload.MessageContent("Olá", null),
                        System.currentTimeMillis()
                ))
                .build();

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "IGNORED_FROM_ME");
        verifyNoInteractions(rabbitTemplate);
    }
}
