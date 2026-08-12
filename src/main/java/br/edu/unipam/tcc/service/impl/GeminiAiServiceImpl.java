package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.config.GeminiProperties;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.dto.gemini.GeminiApiDto.GeminiRequest;
import br.edu.unipam.tcc.dto.gemini.GeminiApiDto.GeminiResponse;
import br.edu.unipam.tcc.entity.InteractionLog;
import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.Student;
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
    public AiIntentResult analyzeMessage(
            Student student,
            String incomingMessage,
            Question activeQuestion,
            InteractionLog lastLog,
            List<String> availableAreas
    ) {
        if (!isAvailable()) {
            log.debug("Gemini AI não está configurado ou está desabilitado. Utilizando fallback local.");
            return AiIntentResult.fallback(incomingMessage);
        }

        try {
            String studentName = student != null ? student.getFullName() : "Estudante";
            String courseName = (student != null && student.getCourse() != null) ? student.getCourse().getName() : "Ensino Superior";
            String tutorPersona = (student != null && student.getCourse() != null)
                    ? student.getCourse().getTutorPersona()
                    : "Você é um Professor e Tutor acadêmico especialista auxiliando estudantes universitários.";
            Integer period = (student != null) ? student.getAcademicPeriod() : 1;

            String areasList = (availableAreas != null && !availableAreas.isEmpty())
                    ? String.join(", ", availableAreas)
                    : "GERAL";

            StringBuilder contextBuilder = new StringBuilder();
            if (activeQuestion != null) {
                contextBuilder.append("\n[QUESTÃO ATIVA AGUARDANDO RESPOSTA]:\n")
                        .append("Enunciado: \"").append(activeQuestion.getStatement()).append("\"\n");
                if (activeQuestion.getOptions() != null) {
                    activeQuestion.getOptions().forEach(opt ->
                            contextBuilder.append(opt.getLetter()).append(") ").append(opt.getOptionText()).append("\n")
                    );
                }
            }

            if (lastLog != null && lastLog.getQuestion() != null) {
                contextBuilder.append("\n[ÚLTIMA QUESTÃO RESPONDIDA RECENTEMENTE]:\n")
                        .append("Enunciado: \"").append(lastLog.getQuestion().getStatement()).append("\"\n")
                        .append("Gabarito/Justificativa: \"").append(lastLog.getQuestion().getEffectiveExplanation()).append("\"\n")
                        .append("Opção escolhida pelo aluno: ").append(lastLog.getSelectedOption())
                        .append(" (Acertou: ").append(lastLog.getIsCorrect()).append(")\n");
            }

            String prompt = String.format("""
                    %s
                    
                    Perfil do Estudante:
                    - Nome: %s
                    - Curso: %s (%dº Período)
                    - Áreas/Disciplinas disponíveis: [%s]
                    %s
                    
                    Mensagem recebida do estudante no WhatsApp: "%s"
                    
                    Instruções de Análise Semântica e NLU:
                    1. Analise o que o aluno quer dizer. Não se limite a palavras exatas.
                    2. Se o aluno estiver tentando responder à [QUESTÃO ATIVA] (seja com a letra diretamente ou discursivamente, ex: "acho que é a B", "com certeza letra C", "segunda alternativa"), classifique como ANSWER_ACTIVE_QUESTION e indique a opção em OPTION.
                    3. Se o aluno estiver tirando dúvidas, pedindo explicação sobre uma questão recente ou conceito teórico ("não entendi", "por que a B está errada?", "me explica a matéria"), classifique como EXPLAIN_CONCEPT.
                    4. Se o aluno quiser iniciar ou receber questões ("revisar", "manda questão", "quero estudar", "bora praticar"), classifique como REQUEST_REVIEW.
                    5. Se o aluno quiser ver notas ou progresso ("como estou?", "desempenho", "estatísticas"), classifique como STATS.
                    6. Se o aluno pedir ajuda ou instruções gerais sobre o bot ("ajuda", "como funciona", "socorro", "menu"), classifique como HELP.
                    7. Se for conversa geral ou saudação ("olá", "bom dia", "valeu"), classifique como GENERAL_CHAT.
                    
                    Responda ESTRITAMENTE no seguinte formato:
                    INTENT: <ANSWER_ACTIVE_QUESTION | EXPLAIN_CONCEPT | REQUEST_REVIEW | STATS | HELP | GENERAL_CHAT | UNKNOWN>
                    OPTION: <A | B | C | D | E | NONE>
                    AREA: <Nome da área/disciplina se mencionada, ou NONE>
                    REPLY: <Mensagem amigável, motivadora e no tom do curso do aluno (máximo 3 frases) em Português do Brasil para enviar no WhatsApp>
                    """,
                    tutorPersona,
                    studentName,
                    courseName,
                    period,
                    areasList,
                    contextBuilder.toString(),
                    incomingMessage
            );

            log.info("🤖 NLU com Google Gemini [{}] para o estudante [{}] ({})", properties.getModel(), studentName, courseName);
            String geminiText = callGeminiApi(prompt);
            return parseAiIntentResponse(geminiText, incomingMessage);

        } catch (Exception e) {
            log.warn("Falha ao processar mensagem com Gemini AI: {}. Utilizando fallback local.", e.getMessage());
            return AiIntentResult.fallback(incomingMessage);
        }
    }

    @Override
    public AiIntentResult analyzeMessage(String studentName, String incomingMessage, List<String> availableSpecialties) {
        Student dummy = Student.builder().fullName(studentName).academicPeriod(1).build();
        return analyzeMessage(dummy, incomingMessage, null, null, availableSpecialties);
    }

    @Override
    public String generateAcademicTutorExplanation(Student student, Question question, String studentDoubt) {
        if (!isAvailable() || question == null) {
            return question != null ? question.getEffectiveExplanation() : "";
        }

        try {
            String studentName = student != null ? student.getFullName() : "Estudante";
            String courseName = (student != null && student.getCourse() != null) ? student.getCourse().getName() : "Ensino Superior";
            String tutorPersona = (student != null && student.getCourse() != null)
                    ? student.getCourse().getTutorPersona()
                    : "Você é um Professor e Tutor especialista auxiliando estudantes universitários.";

            log.info("🎓 Gerando explicação do Tutor Acadêmico [{}] ({}) para o estudante [{}]", properties.getModel(), courseName, studentName);
            String prompt = String.format("""
                    %s Auxilie o estudante %s do curso de %s.
                    
                    Questão / Caso:
                    "%s"
                    
                    Gabarito / Justificativa Oficial:
                    "%s"
                    
                    Dúvida do Estudante:
                    "%s"
                    
                    Instruções:
                    1. Responda de forma didática, encorajadora, respeitosa e direta em Português do Brasil.
                    2. Explique os fundamentos doutrinários, teóricos, normativos ou científicos respondendo diretamente à dúvida do aluno.
                    3. Seja conciso (máximo 4 a 5 parágrafos curtos formatados com negritos para o WhatsApp).
                    """,
                    tutorPersona,
                    studentName,
                    courseName,
                    question.getStatement() != null ? question.getStatement() : "",
                    question.getEffectiveExplanation(),
                    studentDoubt
            );

            String tutorResponse = callGeminiApi(prompt);
            return (tutorResponse != null && !tutorResponse.isBlank()) ? tutorResponse : question.getEffectiveExplanation();

        } catch (Exception e) {
            log.warn("Erro ao gerar explicação de tutor com Gemini: {}. Retornando explicação padrão.", e.getMessage());
            return question.getEffectiveExplanation();
        }
    }

    @Override
    public String generateClinicalTutorExplanation(String studentName, String statement, String clinicalExplanation, String studentDoubt) {
        Question dummy = Question.builder().statement(statement).clinicalExplanation(clinicalExplanation).explanation(clinicalExplanation).build();
        Student dummyStudent = Student.builder().fullName(studentName).build();
        return generateAcademicTutorExplanation(dummyStudent, dummy, studentDoubt);
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
        Character option = null;
        String area = null;
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
            } else if (trimmed.startsWith("OPTION:")) {
                String optStr = trimmed.substring("OPTION:".length()).trim().toUpperCase();
                if (!optStr.equalsIgnoreCase("NONE") && !optStr.isEmpty()) {
                    option = optStr.charAt(0);
                }
            } else if (trimmed.startsWith("AREA:") || trimmed.startsWith("SPECIALTY:")) {
                int colonIdx = trimmed.indexOf(':');
                String areaStr = trimmed.substring(colonIdx + 1).trim();
                if (!areaStr.equalsIgnoreCase("NONE") && !areaStr.isBlank()) {
                    area = areaStr.toUpperCase();
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
                .extractedOption(option)
                .targetKnowledgeArea(area)
                .responseMessage(reply)
                .handledByAi(true)
                .build();
    }
}
