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
        log.info("Processando mensagem recebida na fila RabbitMQ: remetente [{}]", payload.extractSenderPhone());
        try {
            chatbotService.processIncomingMessage(payload);
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do webhook: {}", e.getMessage(), e);
            throw e; // Encaminha para Dead Letter Queue se exceder retries
        }
    }
}
