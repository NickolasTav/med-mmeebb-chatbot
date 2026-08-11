package br.edu.unipam.tcc.consumer;

import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UaiZapInboundConsumer {

    private final ChatbotService chatbotService;

    @RabbitListener(queues = "${rabbitmq.queues.uaizap-inbound:q.uaizap.inbound.messages}")
    public void consumeInboundMessage(UaiZapWebhookPayload payload) {
        if (payload == null) {
            return;
        }

        String phone = payload.extractSenderPhone();
        String messageId = (payload.getData() != null && payload.getData().getKey() != null)
                ? payload.getData().getKey().getId()
                : null;

        br.edu.unipam.tcc.config.CorrelationMdcHelper.setContext(phone, messageId, "RABBITMQ_INBOUND_CONSUME");
        try {
            log.info("🐰 Consumindo mensagem da fila RabbitMQ [q.uaizap.inbound.messages] para o aluno [{}]", phone);
            chatbotService.processIncomingMessage(payload);
            log.info("✅ Mensagem processada pelo ChatbotService com sucesso para o aluno [{}]", phone);
        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem do webhook: {}", e.getMessage(), e);
            throw e; // Encaminha para Dead Letter Queue se exceder retries
        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }
}
