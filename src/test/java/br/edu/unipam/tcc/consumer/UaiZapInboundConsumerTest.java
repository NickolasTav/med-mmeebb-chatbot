package br.edu.unipam.tcc.consumer;

import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UaiZapInboundConsumerTest {

    @Mock
    private ChatbotService chatbotService;

    private UaiZapInboundConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UaiZapInboundConsumer(chatbotService);
    }

    @Test
    @DisplayName("Consumer deve encaminhar payload recebido para o ChatbotService")
    void shouldForwardMessageToChatbotService() {
        UaiZapWebhookPayload payload = UaiZapWebhookPayload.builder()
                .event("messages.upsert")
                .data(new UaiZapWebhookPayload.MessageData(
                        new UaiZapWebhookPayload.MessageKey("5534999999999@s.whatsapp.net", false, "123"),
                        "Student",
                        "conversation",
                        new UaiZapWebhookPayload.MessageContent("B", null),
                        System.currentTimeMillis()
                ))
                .build();

        consumer.consumeInboundMessage(payload);

        verify(chatbotService).processIncomingMessage(payload);
    }
}
