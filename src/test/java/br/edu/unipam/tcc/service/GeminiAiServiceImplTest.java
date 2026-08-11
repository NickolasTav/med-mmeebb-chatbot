package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.GeminiProperties;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.service.impl.GeminiAiServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class GeminiAiServiceImplTest {

    private GeminiProperties properties;
    private GeminiAiServiceImpl geminiAiService;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setApiKey("test_gemini_key");
        properties.setModel("gemini-1.5-flash");
        properties.setBaseUrl("https://generativelanguage.googleapis.com");
        properties.setEnabled(true);
        properties.setTimeoutSeconds(5);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        geminiAiService = new GeminiAiServiceImpl(properties, builder, objectMapper);
    }

    @Test
    @DisplayName("Deve identificar intenção de revisão com especialidade médica quando o aluno solicitar")
    void testAnalyzeMessage_RequestReviewSpecialty() {
        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "INTENT: REQUEST_REVIEW\\nSPECIALTY: PEDIATRIA\\nREPLY: Perfeito! Vou buscar uma questão de Pediatria para você revisar agora."
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test_gemini_key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        AiIntentResult result = geminiAiService.analyzeMessage(
                "Níckolas",
                "Quero praticar casos de pediatria hoje",
                List.of("PEDIATRIA", "CARDIOLOGIA", "GINECOLOGIA")
        );

        assertThat(result).isNotNull();
        assertThat(result.getIntent()).isEqualTo(AiIntentResult.IntentType.REQUEST_REVIEW);
        assertThat(result.getTargetSpecialty()).isEqualTo("PEDIATRIA");
        assertThat(result.isHandledByAi()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve atuar como Tutor Clínico e gerar explicação médica pedagógica")
    void testGenerateClinicalTutorExplanation_Success() {
        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Olá Níckolas! Na crise de asma aguda grave, o uso imediato de beta-2 agonista inalatório é a primeira linha devido ao rápido relaxamento do músculo liso bronquial."
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test_gemini_key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        String explanation = geminiAiService.generateClinicalTutorExplanation(
                "Níckolas",
                "Paciente de 8 anos com crise asmática grave...",
                "O tratamento de primeira linha consiste em Salbutamol inalatório.",
                "Por que não administrar corticoide isolado de início?"
        );

        assertThat(explanation).isNotBlank();
        assertThat(explanation).contains("Olá Níckolas!");
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve fazer fallback seguro quando o Gemini estiver desabilitado ou sem chave")
    void testAnalyzeMessage_FallbackWhenDisabled() {
        properties.setEnabled(false);

        AiIntentResult result = geminiAiService.analyzeMessage("Níckolas", "mensagem qualquer", List.of());

        assertThat(result).isNotNull();
        assertThat(result.isHandledByAi()).isFalse();
        assertThat(result.getIntent()).isEqualTo(AiIntentResult.IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("Deve fazer fallback suave caso ocorra erro HTTP ou instabilidade de rede")
    void testAnalyzeMessage_FallbackOnHttpError() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test_gemini_key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AiIntentResult result = geminiAiService.analyzeMessage("Níckolas", "Quero ajuda com cardiologia", List.of("CARDIOLOGIA"));

        assertThat(result).isNotNull();
        assertThat(result.isHandledByAi()).isFalse();
        mockServer.verify();
    }
}
