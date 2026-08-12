package br.edu.unipam.tcc.consumer;

import br.edu.unipam.tcc.dto.UaiZapSendTextRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Consumidor responsável por despachar mensagens para o WhatsApp através da API do UaiZap.
 * Incorpora mecanismos de proteção Anti-Bloqueio / Anti-Ban da Meta:
 * - Concorrência unitária (concurrency = 1) para fila estritamente sequencial;
 * - Simulação de presença "digitando..." (presence: composing);
 * - Delay parametrizável com variação aleatória (jitter) para humanização do tráfego.
 */
@Slf4j
@Component
public class UaiZapOutboundConsumer {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String instanceToken;
    private final String instanceName;
    private final int delaySeconds;
    private final boolean jitterEnabled;
    private final boolean presenceSimulationEnabled;
    private final int presenceDurationSeconds;

    @Autowired
    public UaiZapOutboundConsumer(
            @Value("${uaizap.api.base-url:https://api.uaizap.com.br}") String baseUrl,
            @Value("${uaizap.api.instance-token:dummy_token}") String instanceToken,
            @Value("${uaizap.api.instance-name:unipam_med_bot}") String instanceName,
            @Value("${uaizap.outbound.delay-seconds:6}") int delaySeconds,
            @Value("${uaizap.outbound.jitter-enabled:true}") boolean jitterEnabled,
            @Value("${uaizap.outbound.presence-simulation-enabled:true}") boolean presenceSimulationEnabled,
            @Value("${uaizap.outbound.presence-duration-seconds:2}") int presenceDurationSeconds
    ) {
        this(new RestTemplate(), baseUrl, instanceToken, instanceName, delaySeconds, jitterEnabled, presenceSimulationEnabled, presenceDurationSeconds);
    }

    public UaiZapOutboundConsumer(
            RestTemplate restTemplate,
            String baseUrl,
            String instanceToken,
            String instanceName,
            int delaySeconds,
            boolean jitterEnabled,
            boolean presenceSimulationEnabled,
            int presenceDurationSeconds
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.instanceToken = instanceToken;
        this.instanceName = instanceName;
        this.delaySeconds = delaySeconds;
        this.jitterEnabled = jitterEnabled;
        this.presenceSimulationEnabled = presenceSimulationEnabled;
        this.presenceDurationSeconds = presenceDurationSeconds;
    }

    @RabbitListener(
            queues = "${rabbitmq.queues.outbound:q.uaizap.outbound}",
            concurrency = "${rabbitmq.listeners.outbound.concurrency:1}"
    )
    public void consumeOutboundDispatch(UaiZapSendTextRequest request) {
        if (request == null || request.getNumber() == null || request.getText() == null || request.getNumber().isBlank()) {
            log.warn("Requisição de disparo descartada: payload nulo ou incompleto");
            return;
        }

        br.edu.unipam.tcc.config.CorrelationMdcHelper.setContext(request.getNumber(), null, "OUTBOUND_DISPATCH");
        try {
            log.info("🚀 [OUTBOUND] Consumindo mensagem para envio ao WhatsApp [{}] via UaiZap API", request.getNumber());

            // 1. Simulação de Presença ("composing" / digitando)
            if (presenceSimulationEnabled) {
                simulateTypingPresence(request.getNumber());
                if (presenceDurationSeconds > 0) {
                    sleep(presenceDurationSeconds * 1000L);
                }
            }

            // 2. Envio da Mensagem de Texto
            sendTextMessage(request.getNumber(), request.getText());
            log.info("📲 [OUTBOUND] Mensagem enviada com sucesso para o WhatsApp [{}]", request.getNumber());

            // 3. Aplicação do Delay Anti-Spam / Anti-Ban com Jitter
            long totalDelayMs = calculateEffectiveDelayMs();
            long spentDelayMs = (presenceSimulationEnabled && presenceDurationSeconds > 0) ? (presenceDurationSeconds * 1000L) : 0L;
            long remainingDelayMs = Math.max(0L, totalDelayMs - spentDelayMs);

            if (remainingDelayMs > 0) {
                log.info("⏱️ [OUTBOUND] Aplicando delay seguro de {}ms para próximo disparo (Anti-Ban Meta)", remainingDelayMs);
                sleep(remainingDelayMs);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao despachar mensagem via UaiZap: {}", e.getMessage(), e);
        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }

    /**
     * Envia o evento de presença "digitando..." para simular interação humana no chat.
     */
    public void simulateTypingPresence(String number) {
        try {
            if (isSimulationEnvironment()) {
                log.info("[SIMULAÇÃO UAIZAP] Enviando presença 'composing' para [{}] (duração: {}s)...", number, presenceDurationSeconds);
                return;
            }

            HttpHeaders headers = createHeaders();
            Map<String, Object> body = Map.of(
                    "number", number,
                    "presence", "composing"
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String uazapiUrl = baseUrl.endsWith("/") ? (baseUrl + "chat/presence") : (baseUrl + "/chat/presence");
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(uazapiUrl, entity, String.class);
                log.debug("Resposta UaiZap Presença (/chat/presence): status [{}]", response.getStatusCode());
            } catch (Exception e) {
                String legacyUrl = String.format("%s/chat/sendPresence/%s", baseUrl, instanceName);
                Map<String, Object> legacyBody = Map.of(
                        "number", number,
                        "presence", "composing",
                        "delay", Math.max(1, presenceDurationSeconds) * 1000
                );
                HttpEntity<Map<String, Object>> legacyEntity = new HttpEntity<>(legacyBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(legacyUrl, legacyEntity, String.class);
                log.debug("Resposta UaiZap Presença (/chat/sendPresence): status [{}]", response.getStatusCode());
            }

        } catch (Exception e) {
            log.warn("Falha não-bloqueante ao simular presença no UaiZap para [{}]: {}", number, e.getMessage());
        }
    }

    /**
     * Executa o disparo do texto para o endpoint de mensagens do UaiZap.
     */
    public void sendTextMessage(String number, String text) {
        if (isSimulationEnvironment()) {
            log.info("[SIMULAÇÃO UAIZAP] Mensagem enviada para [{}]:\n{}", number, text);
            return;
        }

        HttpHeaders headers = createHeaders();
        Map<String, Object> body = Map.of(
                "number", number,
                "text", text
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String uazapiUrl = baseUrl.endsWith("/") ? (baseUrl + "send/text") : (baseUrl + "/send/text");
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(uazapiUrl, entity, String.class);
            log.info("Resposta UaiZap API (/send/text): status [{}]", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Tentativa em /send/text falhou ({}), tentando rota alternativa /message/sendText/{}", e.getMessage(), instanceName);
            String legacyUrl = String.format("%s/message/sendText/%s", baseUrl, instanceName);
            ResponseEntity<String> response = restTemplate.postForEntity(legacyUrl, entity, String.class);
            log.info("Resposta UaiZap API (/message/sendText): status [{}]", response.getStatusCode());
        }
    }

    /**
     * Calcula o delay dinâmico com variação aleatória (jitter) para evitar padrões mecânicos detectados pela Meta.
     */
    public long calculateEffectiveDelayMs() {
        if (delaySeconds <= 0) {
            return 0L;
        }

        long baseDelayMs = delaySeconds * 1000L;
        if (!jitterEnabled) {
            return baseDelayMs;
        }

        // Variação pseudoaleatória entre -1.5s e +1.5s com piso seguro de 3000ms
        long jitter = ThreadLocalRandom.current().nextLong(-1500, 1501);
        long effective = baseDelayMs + jitter;
        return Math.max(3000L, effective);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", instanceToken);
        headers.set("apikey", instanceToken);
        return headers;
    }

    private boolean isSimulationEnvironment() {
        return baseUrl.contains("localhost") || instanceToken.equals("dummy_token") || instanceToken.equals("test_token");
    }

    protected void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread de envio do WhatsApp interrompida durante o delay anti-ban: {}", e.getMessage());
        }
    }
}
