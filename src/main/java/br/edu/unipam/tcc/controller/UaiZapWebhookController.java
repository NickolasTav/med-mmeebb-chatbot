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
    private final String directExchange;
    private final String inboundRoutingKey;

    public UaiZapWebhookController(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchanges.med-direct:ex.med.direct}") String directExchange,
            @Value("${rabbitmq.routing-keys.inbound:rk.uaizap.inbound}") String inboundRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.directExchange = directExchange;
        this.inboundRoutingKey = inboundRoutingKey;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody UaiZapWebhookPayload payload) {
        if (payload.getData() != null && payload.getData().getKey() != null && Boolean.TRUE.equals(payload.getData().getKey().getFromMe())) {
            return ResponseEntity.ok(Map.of("status", "IGNORED_FROM_ME"));
        }

        String phone = payload.extractSenderPhone();
        String messageId = (payload.getData() != null && payload.getData().getKey() != null)
                ? payload.getData().getKey().getId()
                : null;

        br.edu.unipam.tcc.config.CorrelationMdcHelper.setContext(phone, messageId, "WEBHOOK_RECEIVE");
        try {
            log.info("📥 Webhook UaiZap recebido: evento [{}] do remetente [{}] (MessageId: {})", 
                    payload.getEvent(), phone, messageId);

            // Envia imediatamente para a fila assíncrona RabbitMQ (SLA < 100ms)
            rabbitTemplate.convertAndSend(directExchange, inboundRoutingKey, payload);
            log.info("🐰 Mensagem enfileirada no RabbitMQ [Exchange: '{}', Key: '{}'] com sucesso", directExchange, inboundRoutingKey);

            return ResponseEntity.ok(Map.of("status", "QUEUED", "event", payload.getEvent()));
        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }
}
