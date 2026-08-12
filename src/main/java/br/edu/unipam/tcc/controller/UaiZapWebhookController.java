package br.edu.unipam.tcc.controller;

import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/uaizap")
public class UaiZapWebhookController {

    private final RabbitTemplate rabbitTemplate;
    private final String inboundQueue;

    public UaiZapWebhookController(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.queues.inbound:q.uaizap.inbound}") String inboundQueue
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.inboundQueue = inboundQueue;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody(required = false) UaiZapWebhookPayload payload) {
        if (payload == null) {
            return ResponseEntity.ok(Map.of("status", "IGNORED_EMPTY"));
        }

        if (payload.isFromMe()) {
            return ResponseEntity.ok(Map.of("status", "IGNORED_FROM_ME"));
        }

        if (payload.isGroup()) {
            return ResponseEntity.ok(Map.of("status", "IGNORED_GROUP"));
        }

        String phone = payload.extractSenderPhone();
        String messageId = payload.extractMessageId();
        String eventName = payload.getEffectiveEvent();

        br.edu.unipam.tcc.config.CorrelationMdcHelper.setContext(phone, messageId, "WEBHOOK_RECEIVE");
        try {
            log.info("📥 Webhook UaiZap recebido: evento [{}] do remetente [{}] (MessageId: {})", 
                    eventName, phone, messageId);

            rabbitTemplate.convertAndSend(inboundQueue, payload);
            log.info("🐰 Mensagem enfileirada no RabbitMQ [Fila: '{}'] com sucesso", inboundQueue);

            java.util.Map<String, Object> responseBody = new java.util.LinkedHashMap<>();
            responseBody.put("status", "QUEUED");
            responseBody.put("event", eventName);

            return ResponseEntity.ok(responseBody);
        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }
}
