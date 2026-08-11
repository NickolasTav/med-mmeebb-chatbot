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

    @Test
    @DisplayName("Deve processar com segurança webhook quando o evento for nulo sem lançar NullPointerException")
    void shouldHandleWebhookWhenEventIsNull() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event(null)
                .data(new UaiZapWebhookPayload.MessageData(
                        new UaiZapWebhookPayload.MessageKey("5534999999999@s.whatsapp.net", false, "MSG_NULL_EVENT"),
                        "Student",
                        "conversation",
                        new UaiZapWebhookPayload.MessageContent("Olá", null),
                        System.currentTimeMillis()
                ))
                .build();

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "QUEUED");
        verify(rabbitTemplate).convertAndSend(eq(exchange), eq(routingKey), eq(payload));
    }

    @Test
    @DisplayName("Deve retornar status OK mesmo quando payload for nulo")
    void shouldHandleWebhookWhenPayloadIsNull() {
        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "IGNORED_EMPTY");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Deve processar payload nativo da Uazapi (EventType, message, chat)")
    void shouldHandleNativeUazapiPayload() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event("messages")
                .instance("teste")
                .baseUrl("https://free.uazapi.com")
                .chat(UaiZapWebhookPayload.UazapiChat.builder()
                        .name("Rafael Faculdade")
                        .phone("+55 34 9821-7808")
                        .wa_chatid("553498217808@s.whatsapp.net")
                        .wa_name("Rafael")
                        .wa_isGroup(false)
                        .build())
                .message(UaiZapWebhookPayload.UazapiMessage.builder()
                        .chatid("553498217808@s.whatsapp.net")
                        .sender_pn("553498217808@s.whatsapp.net")
                        .senderName("Rafael")
                        .text("olá?")
                        .fromMe(false)
                        .isGroup(false)
                        .id("553499754953:ACD9E19035A6AA4327A704AE66A81B45")
                        .messageid("ACD9E19035A6AA4327A704AE66A81B45")
                        .build())
                .build();

        assertThat(payload.extractSenderPhone()).isEqualTo("553498217808");
        assertThat(payload.extractMessageText()).isEqualTo("olá?");
        assertThat(payload.extractSenderName()).isEqualTo("Rafael");
        assertThat(payload.extractMessageId()).isEqualTo("ACD9E19035A6AA4327A704AE66A81B45");
        assertThat(payload.isFromMe()).isFalse();
        assertThat(payload.isGroup()).isFalse();

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "QUEUED");
        assertThat(response.getBody()).containsEntry("event", "messages");
        verify(rabbitTemplate).convertAndSend(eq(exchange), eq(routingKey), eq(payload));
    }

    @Test
    @DisplayName("Deve ignorar mensagens enviadas em grupos")
    void shouldIgnoreGroupMessages() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event("messages")
                .message(UaiZapWebhookPayload.UazapiMessage.builder()
                        .chatid("123456789@g.us")
                        .text("Mensagem no grupo")
                        .fromMe(false)
                        .isGroup(true)
                        .build())
                .build();

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "IGNORED_GROUP");
        verifyNoInteractions(rabbitTemplate);
    }
}
