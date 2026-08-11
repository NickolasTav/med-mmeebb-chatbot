package br.edu.unipam.tcc.consumer;

import br.edu.unipam.tcc.dto.UaiZapSendTextRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UaiZapOutboundConsumerTest {

    @Mock
    private RestTemplate restTemplate;

    private UaiZapOutboundConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UaiZapOutboundConsumer(
                restTemplate,
                "https://api.uaizap.com.br",
                "prod_token_123",
                "unipam_med_bot",
                0, // delay 0 para testes rápidos
                false,
                true,
                0 // 0s para testes rápidos
        );
    }

    @Test
    @DisplayName("Deve enviar mensagem de texto e presença para a API do UaiZap em ambiente real")
    void shouldSendTextMessageAndPresenceViaApi() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"SUCCESS\"}", HttpStatus.OK));

        UaiZapSendTextRequest request = UaiZapSendTextRequest.builder()
                .number("5534999999999")
                .text("Olá, estudante! Esta é a sua revisão MMEEBB.")
                .build();

        consumer.consumeOutboundDispatch(request);

        // Verifica envio de presença e envio de texto (padrão oficial UAZAPI /send/text e /chat/presence)
        verify(restTemplate, times(1)).postForEntity(
                eq("https://api.uaizap.com.br/chat/presence"),
                any(HttpEntity.class),
                eq(String.class)
        );

        verify(restTemplate, times(1)).postForEntity(
                eq("https://api.uaizap.com.br/send/text"),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    @DisplayName("Deve não realizar chamadas HTTP reais quando em ambiente de simulação/teste")
    void shouldNotCallHttpInSimulationEnvironment() {
        UaiZapOutboundConsumer simConsumer = new UaiZapOutboundConsumer(
                restTemplate,
                "http://localhost:8989",
                "test_token",
                "test_instance",
                0,
                false,
                false,
                0
        );

        UaiZapSendTextRequest request = UaiZapSendTextRequest.builder()
                .number("5534999999999")
                .text("Teste de simulação")
                .build();

        simConsumer.consumeOutboundDispatch(request);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Deve calcular delay exato quando jitter estiver desativado")
    void shouldCalculateExactDelayWithoutJitter() {
        UaiZapOutboundConsumer c = new UaiZapOutboundConsumer(
                restTemplate, "https://api.uaizap.com.br", "token", "instance",
                6, false, false, 0
        );

        assertThat(c.calculateEffectiveDelayMs()).isEqualTo(6000L);
    }

    @Test
    @DisplayName("Deve retornar delay zero quando delay configurado for menor ou igual a zero")
    void shouldReturnZeroDelayWhenDisabled() {
        UaiZapOutboundConsumer c = new UaiZapOutboundConsumer(
                restTemplate, "https://api.uaizap.com.br", "token", "instance",
                0, false, false, 0
        );

        assertThat(c.calculateEffectiveDelayMs()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Deve calcular delay com jitter dentro da faixa esperada e respeitando limite mínimo")
    void shouldCalculateDelayWithJitterWithinRange() {
        UaiZapOutboundConsumer c = new UaiZapOutboundConsumer(
                restTemplate, "https://api.uaizap.com.br", "token", "instance",
                6, true, false, 0
        );

        for (int i = 0; i < 20; i++) {
            long delay = c.calculateEffectiveDelayMs();
            assertThat(delay).isGreaterThanOrEqualTo(3000L);
            assertThat(delay).isBetween(4500L, 7500L);
        }
    }

    @Test
    @DisplayName("Deve ignorar request nulo ou com campos vazios sem lançar exceção")
    void shouldHandleNullOrEmptyRequestGracefully() {
        assertDoesNotThrow(() -> consumer.consumeOutboundDispatch(null));
        assertDoesNotThrow(() -> consumer.consumeOutboundDispatch(UaiZapSendTextRequest.builder().build()));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Deve capturar e tratar erro de RestClientException sem quebrar execução")
    void shouldHandleRestClientExceptionGracefully() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("Falha na conexão com UaiZap"));

        UaiZapSendTextRequest request = UaiZapSendTextRequest.builder()
                .number("5534999999999")
                .text("Mensagem")
                .build();

        assertDoesNotThrow(() -> consumer.consumeOutboundDispatch(request));
    }
}
