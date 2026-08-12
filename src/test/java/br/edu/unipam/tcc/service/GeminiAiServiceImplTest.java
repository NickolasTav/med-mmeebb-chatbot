package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.GeminiProperties;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.Course;
import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.QuestionOption;
import br.edu.unipam.tcc.entity.Student;
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
    @DisplayName("Deve extrair intenção ANSWER_ACTIVE_QUESTION e letra da opção em resposta discursiva do aluno")
    void testAnalyzeMessage_DiscursiveAnswerExtraction() {
        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "INTENT: ANSWER_ACTIVE_QUESTION\\nOPTION: B\\nAREA: DIR_CONSTITUCIONAL\\nREPLY: Entendido! Processando sua resposta..."
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

        Course lawCourse = Course.builder().code("DIREITO").name("Direito").tutorPersona("Você é um Professor Jurista").build();
        Student student = Student.builder().fullName("Mariana").course(lawCourse).academicPeriod(7).build();
        Question activeQuestion = Question.builder()
                .statement("Qual remédio constitucional é cabível...")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Mandado de Segurança").build(),
                        QuestionOption.builder().letter('B').optionText("Habeas Data").build()
                ))
                .build();

        AiIntentResult result = geminiAiService.analyzeMessage(
                student,
                "Acho que a resposta certa é a alternativa B por se tratar de dados pessoais",
                activeQuestion,
                null,
                List.of("DIR_CONSTITUCIONAL", "DIR_PENAL")
        );

        assertThat(result).isNotNull();
        assertThat(result.getIntent()).isEqualTo(AiIntentResult.IntentType.ANSWER_ACTIVE_QUESTION);
        assertThat(result.getExtractedOption()).isEqualTo('B');
        assertThat(result.getTargetKnowledgeArea()).isEqualTo("DIR_CONSTITUCIONAL");
        assertThat(result.isHandledByAi()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve atuar como Tutor Acadêmico para Direito e Engenharia de Software")
    void testGenerateAcademicTutorExplanation_LawCourse() {
        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Olá Mariana! Conforme o art. 5º, LXXII da CF/88, o Habeas Data protege o direito de acesso e retificação de informações pessoais."
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

        Course lawCourse = Course.builder().code("DIREITO").name("Direito").tutorPersona("Você é um Professor Jurista").build();
        Student student = Student.builder().fullName("Mariana").course(lawCourse).academicPeriod(7).build();
        Question question = Question.builder()
                .statement("Cabimento do Habeas Data")
                .clinicalExplanation("Art. 5º, LXXII da CF/88")
                .explanation("Art. 5º, LXXII da CF/88")
                .build();

        String explanation = geminiAiService.generateAcademicTutorExplanation(
                student,
                question,
                "Por que não cabe Mandado de Segurança aqui?"
        );

        assertThat(explanation).isNotBlank();
        assertThat(explanation).contains("Olá Mariana!");
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
