package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.config.GeminiProperties;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.dto.gemini.GeminiApiDto;
import br.edu.unipam.tcc.dto.gemini.GeminiApiDto.GeminiRequest;
import br.edu.unipam.tcc.dto.gemini.GeminiApiDto.GeminiResponse;
import br.edu.unipam.tcc.service.GeminiAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
public class GeminiAiServiceImpl implements GeminiAiService {

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiAiServiceImpl(GeminiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    public AiIntentResult analyzeMessage(String studentName, String incomingMessage, List<String> availableSpecialties) {
        if (!isAvailable()) {
            log.debug("Gemini AI não está configurado ou está desabilitado. Utilizando fallback determinístico.");
            return AiIntentResult.fallback(incomingMessage);
        }

        try {
            log.info("🤖 Classificando intenção com Google Gemini [{}] para o estudante [{}]", properties.getModel(), studentName);
            String specialtiesList = (availableSpecialties != null && !availableSpecialties.isEmpty())
                    ? String.join(", ", availableSpecialties)
                    : "PEDIATRIA, CLINICA_MEDICA, CIRURGIA_GERAL, GINECOLOGIA_OBSTETRICIA, SAUDE_COLETIVA";

            String prompt = String.format("""
                    Você é o assistente inteligente do Chatbot MMEEBB de Repetição Espaçada para estudantes de Medicina (Internato/Residência) da UNIPAM.
                    Nome do Aluno: %s
                    Especialidades médicas cadastradas: [%s]
                    
                    Mensagem recebida do estudante: "%s"
                    
                    Analise a intenção do aluno e responda estritamente no seguinte formato:
                    INTENT: <REQUEST_REVIEW | EXPLAIN_CONCEPT | STATS | HELP | GENERAL_CHAT | UNKNOWN>
                    SPECIALTY: <Nome da especialidade médica se mencionada, ou NONE>
                    REPLY: <Mensagem curta, amigável e motivadora (máximo 3 frases) em Português do Brasil para enviar ao aluno no WhatsApp>
                    """,
                    studentName != null ? studentName : "Estudante",
                    specialtiesList,
                    incomingMessage
            );

            String geminiText = callGeminiApi(prompt);
            return parseAiIntentResponse(geminiText, incomingMessage);

        } catch (Exception e) {
            log.warn("Falha ao processar mensagem com Gemini AI: {}. Utilizando fallback local.", e.getMessage());
            return AiIntentResult.fallback(incomingMessage);
        }
    }

    @Override
    public String generateClinicalTutorExplanation(String studentName, String statement, String clinicalExplanation, String studentDoubt) {
        if (!isAvailable()) {
            return clinicalExplanation;
        }

        try {
            log.info("🩺 Gerando explicação do Tutor Clínico com Google Gemini [{}] para o estudante [{}]", properties.getModel(), studentName);
            String prompt = String.format("""
                    Você é um Preceptor e Tutor Médico especialista auxiliando um interno de medicina chamado %s.
                    
                    Caso Clínico:
                    "%s"
                    
                    Gabarito / Justificativa Oficial:
                    "%s"
                    
                    Dúvida do Estudante:
                    "%s"
                    
                    Instruções:
                    1. Responda de forma didática, encorajadora e direta em Português do Brasil.
                    2. Explique a fisiopatologia ou a diretriz clínica relevante respondendo especificamente à dúvida do aluno.
                    3. Seja conciso (máximo 4 a 5 parágrafos curtos formatados para o WhatsApp).
                    """,
                    studentName != null ? studentName : "Colega",
                    statement != null ? statement : "",
                    clinicalExplanation != null ? clinicalExplanation : "",
                    studentDoubt
            );

            String tutorResponse = callGeminiApi(prompt);
            return (tutorResponse != null && !tutorResponse.isBlank()) ? tutorResponse : clinicalExplanation;

        } catch (Exception e) {
            log.warn("Erro ao gerar explicação de tutor com Gemini: {}. Retornando explicação padrão.", e.getMessage());
            return clinicalExplanation;
        }
    }

    private String callGeminiApi(String prompt) {
        String uri = String.format("/v1beta/models/%s:generateContent?key=%s", properties.getModel(), properties.getApiKey());
        GeminiRequest request = GeminiRequest.ofSingleText(prompt);

        GeminiResponse response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        return response != null ? response.extractFirstText() : "";
    }

    private AiIntentResult parseAiIntentResponse(String rawText, String fallbackMessage) {
        if (rawText == null || rawText.isBlank()) {
            return AiIntentResult.fallback(fallbackMessage);
        }

        AiIntentResult.IntentType intent = AiIntentResult.IntentType.UNKNOWN;
        String specialty = null;
        String reply = null;

        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("INTENT:")) {
                String intentStr = trimmed.substring("INTENT:".length()).trim().toUpperCase();
                try {
                    intent = AiIntentResult.IntentType.valueOf(intentStr);
                } catch (IllegalArgumentException ignored) {
                    intent = AiIntentResult.IntentType.UNKNOWN;
                }
            } else if (trimmed.startsWith("SPECIALTY:")) {
                String specStr = trimmed.substring("SPECIALTY:".length()).trim();
                if (!specStr.equalsIgnoreCase("NONE") && !specStr.isBlank()) {
                    specialty = specStr.toUpperCase();
                }
            } else if (trimmed.startsWith("REPLY:")) {
                reply = trimmed.substring("REPLY:".length()).trim();
            }
        }

        if (reply == null || reply.isBlank()) {
            reply = rawText;
        }

        return AiIntentResult.builder()
                .intent(intent)
                .targetSpecialty(specialty)
                .responseMessage(reply)
                .handledByAi(true)
                .build();
    }
}
