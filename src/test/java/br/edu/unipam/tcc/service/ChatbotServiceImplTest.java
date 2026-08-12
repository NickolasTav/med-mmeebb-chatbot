package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.I18nConfig;
import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.dto.UnipamStudentProfileDto;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.*;
import br.edu.unipam.tcc.repository.*;
import br.edu.unipam.tcc.service.impl.ChatbotServiceImpl;
import br.edu.unipam.tcc.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ReviewScheduleRepository reviewScheduleRepository;

    @Mock
    private InteractionLogRepository interactionLogRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private MmeebbEngineService mmeebbEngineService;

    @Mock
    private WhatsAppMessageSender messageSender;

    @Mock
    private GeminiAiService geminiAiService;

    @Mock
    private br.edu.unipam.tcc.service.AppMetricsService appMetricsService;

    @Mock
    private UnipamAcademicIntegrationService unipamAcademicIntegrationService;

    private MessageService messageService;
    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(I18nConfig.DEFAULT_LOCALE);
        messageSource.setFallbackToSystemLocale(false);

        messageService = new MessageServiceImpl(messageSource);

        chatbotService = new ChatbotServiceImpl(
                studentRepository,
                courseRepository,
                reviewScheduleRepository,
                interactionLogRepository,
                specialtyRepository,
                mmeebbEngineService,
                messageSender,
                messageService,
                geminiAiService,
                appMetricsService,
                unipamAcademicIntegrationService
        );
    }

    @Test
    @DisplayName("Para novo estudante com telefone na UNIPAM, deve identificar RA, associar ao curso e enviar boas-vindas i18n")
    void shouldRegisterNewStudentWithCourseFromUnipamIntegration() {
        String phone = "5534999999999";
        Course medCourse = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamStudentProfileDto profile = UnipamStudentProfileDto.builder()
                .ra("23000388")
                .fullName("Níckolas Tavares")
                .phoneNumber(phone)
                .courseCode("MEDICINA")
                .courseName("Medicina")
                .academicPeriod(9)
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByPhoneNumber(phone)).thenReturn(Optional.of(profile));
        when(courseRepository.findByCodeIgnoreCase("MEDICINA")).thenReturn(Optional.of(medCourse));
        when(studentRepository.findByRa("23000388")).thenReturn(Optional.empty());

        UaiZapWebhookPayload payload = createPayload(phone, "Olá");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getRa()).isEqualTo("23000388");
        assertThat(studentCaptor.getValue().getFullName()).isEqualTo("Níckolas Tavares");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Bem-vindo(a) ao *Bot MMEEBB de Repetição Espaçada (Medicina - UNIPAM)*");
        assertThat(msgCaptor.getValue()).contains("RA:* 23000388");
    }

    @Test
    @DisplayName("Quando telefone não for encontrado na UNIPAM e mensagem não for RA, deve orientar o aluno a informar seu RA")
    void shouldPromptForRaWhenPhoneNumberNotFoundInUnipam() {
        String phone = "5534977770000";

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByPhoneNumber(phone)).thenReturn(Optional.empty());

        UaiZapWebhookPayload payload = createPayload(phone, "Olá, quero estudar!");

        chatbotService.processIncomingMessage(payload);

        verify(studentRepository, never()).save(any());
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Não localizamos uma matrícula institucional associada a este número de WhatsApp");
        assertThat(msgCaptor.getValue()).contains("digite apenas o seu RA institucional");
    }

    @Test
    @DisplayName("Quando estudante envia seu RA no chat, deve validar na UNIPAM, vincular o novo telefone e cadastrar")
    void shouldRegisterStudentWhenUserSendsValidRa() {
        String phone = "5534977770000";
        String ra = "23000388";
        Course medCourse = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamStudentProfileDto profile = UnipamStudentProfileDto.builder()
                .ra(ra)
                .fullName("Níckolas Tavares")
                .phoneNumber(phone)
                .courseCode("MEDICINA")
                .courseName("Medicina")
                .academicPeriod(9)
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByRa(ra)).thenReturn(Optional.of(profile));
        when(unipamAcademicIntegrationService.linkPhoneNumberToRa(ra, phone)).thenReturn(profile);
        when(courseRepository.findByCodeIgnoreCase("MEDICINA")).thenReturn(Optional.of(medCourse));
        when(studentRepository.findByRa(ra)).thenReturn(Optional.empty());

        UaiZapWebhookPayload payload = createPayload(phone, ra);

        chatbotService.processIncomingMessage(payload);

        verify(unipamAcademicIntegrationService).linkPhoneNumberToRa(ra, phone);
        verify(studentRepository).save(any(Student.class));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("RA 23000388 vinculado com sucesso!");
        assertThat(msgCaptor.getValue()).contains("Níckolas Tavares");
    }

    @Test
    @DisplayName("Quando estudante envia RA inválido/inexistente na UNIPAM, deve retornar aviso institucional")
    void shouldNotifyWhenUserSendsInvalidRa() {
        String phone = "5534977770000";
        String invalidRa = "99999999";

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(unipamAcademicIntegrationService.findStudentByRa(invalidRa)).thenReturn(Optional.empty());

        UaiZapWebhookPayload payload = createPayload(phone, invalidRa);

        chatbotService.processIncomingMessage(payload);

        verify(studentRepository, never()).save(any());
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Não localizamos nenhum registro acadêmico ativo na UNIPAM com o RA *99999999*");
    }

    @Test
    @DisplayName("Deve responder ao comando rápido '/ajuda' com texto i18n via Fast-Path")
    void shouldRespondToFastPathHelpCommand() {
        String phone = "5534999999999";
        Student student = Student.builder().phoneNumber(phone).fullName("Lucas").build();
        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));

        UaiZapWebhookPayload payload = createPayload(phone, "/ajuda");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("CENTRAL DE AJUDA - BOT MMEEBB");
        verifyNoInteractions(geminiAiService); // Fast-Path sem consumo de tokens
    }

    @Test
    @DisplayName("Deve responder ao comando '/stats' com métricas acadêmicas por curso, período e RA")
    void shouldRespondToStatsCommandWithAcademicMetrics() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Course lawCourse = Course.builder().name("Direito").build();
        Student student = Student.builder()
                .id(studentId)
                .ra("23000388")
                .phoneNumber(phone)
                .fullName("Paula")
                .course(lawCourse)
                .academicPeriod(5)
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(studentId)).thenReturn(List.of(
                InteractionLog.builder().isCorrect(true).build(),
                InteractionLog.builder().isCorrect(true).build(),
                InteractionLog.builder().isCorrect(false).build()
        ));
        when(reviewScheduleRepository.findByStudentId(studentId)).thenReturn(List.of(
                ReviewSchedule.builder().id(1L).build()
        ));

        UaiZapWebhookPayload payload = createPayload(phone, "/stats");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("SEU DESEMPENHO ACADÊMICO (MMEEBB)");
        assertThat(msgCaptor.getValue()).contains("Aluno:* Paula (RA: 23000388)");
        assertThat(msgCaptor.getValue()).contains("Curso:* Direito (5º Período)");
        assertThat(msgCaptor.getValue()).contains("Total de Questões Respondidas: *3*");
        assertThat(msgCaptor.getValue()).contains("Taxa de Retenção / Precisão: *66,7%*");
        verifyNoInteractions(geminiAiService);
    }

    @Test
    @DisplayName("Deve processar resposta atômica de questão e atualizar o ciclo MMEEBB")
    void shouldProcessAtomicQuestionAnswerAndAdvanceMmeebb() {
        String phone = "5534999999999";
        UUID studentId = UUID.randomUUID();
        Course medCourse = Course.builder().name("Medicina").build();
        Specialty specialty = Specialty.builder().code("CLINICA_MEDICA").course(medCourse).build();
        Topic topic = Topic.builder().specialty(specialty).build();
        Student student = Student.builder().id(studentId).phoneNumber(phone).course(medCourse).build();

        Question question = Question.builder()
                .id(10L)
                .topic(topic)
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Opção A").isCorrect(false).build(),
                        QuestionOption.builder().letter('B').optionText("Opção B").isCorrect(true).build()
                ))
                .clinicalExplanation("Explicação da B")
                .build();

        ReviewSchedule schedule = ReviewSchedule.builder()
                .id(1L)
                .student(student)
                .question(question)
                .nIndex(0)
                .consecutiveCorrect(0)
                .repetitionCount(0)
                .status("PENDING")
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId))
                .thenReturn(List.of(schedule))
                .thenReturn(List.of()); // Após salvar, não há mais pendentes

        when(mmeebbEngineService.processReviewFeedback(eq(0), eq(0), eq(true), any(LocalDate.class)))
                .thenReturn(MmeebbCalculationResult.builder().nIndex(1).intervalDays(2).consecutiveCorrect(1).nextDueDate(LocalDate.now().plusDays(2)).build());

        UaiZapWebhookPayload payload = createPayload(phone, "b");

        chatbotService.processIncomingMessage(payload);

        verify(reviewScheduleRepository).save(schedule);
        verify(interactionLogRepository).save(any(InteractionLog.class));
        verify(appMetricsService).recordReviewAnswer(true, "CLINICA_MEDICA");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender, atLeastOnce()).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getAllValues().get(0)).contains("PARABÉNS! RESPOSTA CORRETA!");
        assertThat(msgCaptor.getAllValues().get(0)).contains("n = 1");
    }

    @Test
    @DisplayName("Deve acionar NLU com Gemini quando mensagem for discursiva e resolver resposta da questão")
    void shouldInvokeGeminiNluForDiscursiveMessage() {
        String phone = "5534999999999";
        UUID studentId = UUID.randomUUID();
        Course medCourse = Course.builder().name("Medicina").build();
        Student student = Student.builder().id(studentId).phoneNumber(phone).course(medCourse).build();

        Question question = Question.builder()
                .id(10L)
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Opção A").isCorrect(false).build(),
                        QuestionOption.builder().letter('B').optionText("Opção B").isCorrect(true).build()
                ))
                .clinicalExplanation("Explicação da B")
                .build();

        ReviewSchedule schedule = ReviewSchedule.builder()
                .id(1L)
                .student(student)
                .question(question)
                .nIndex(0)
                .consecutiveCorrect(0)
                .repetitionCount(0)
                .status("PENDING")
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId))
                .thenReturn(List.of(schedule))
                .thenReturn(List.of());

        AiIntentResult aiResult = AiIntentResult.builder()
                .intent(AiIntentResult.IntentType.ANSWER_ACTIVE_QUESTION)
                .extractedOption('B')
                .handledByAi(true)
                .build();

        when(geminiAiService.analyzeMessage(eq(student), anyString(), eq(question), any(), any()))
                .thenReturn(aiResult);

        when(mmeebbEngineService.processReviewFeedback(eq(0), eq(0), eq(true), any(LocalDate.class)))
                .thenReturn(MmeebbCalculationResult.builder().nIndex(1).intervalDays(2).consecutiveCorrect(1).nextDueDate(LocalDate.now().plusDays(2)).build());

        UaiZapWebhookPayload payload = createPayload(phone, "Acredito com certeza que a opção correta seja a letra B!");

        chatbotService.processIncomingMessage(payload);

        verify(geminiAiService).analyzeMessage(eq(student), contains("letra B"), eq(question), any(), any());
        verify(reviewScheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("Deve acionar Tutor Acadêmico com Gemini quando intenção for EXPLAIN_CONCEPT")
    void shouldInvokeAcademicTutorWhenIntentIsExplainConcept() {
        String phone = "5534999999999";
        UUID studentId = UUID.randomUUID();
        Course lawCourse = Course.builder().name("Direito").build();
        Student student = Student.builder().id(studentId).phoneNumber(phone).course(lawCourse).build();

        Question question = Question.builder().id(20L).explanation("Explicação Jurídica").build();
        ReviewSchedule schedule = ReviewSchedule.builder().id(2L).student(student).question(question).build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId)).thenReturn(List.of(schedule));

        AiIntentResult aiResult = AiIntentResult.builder()
                .intent(AiIntentResult.IntentType.EXPLAIN_CONCEPT)
                .handledByAi(true)
                .build();

        when(geminiAiService.analyzeMessage(eq(student), anyString(), eq(question), any(), any()))
                .thenReturn(aiResult);

        when(geminiAiService.generateAcademicTutorExplanation(eq(student), eq(question), anyString()))
                .thenReturn("No direito constitucional, o remédio cabível é...");

        UaiZapWebhookPayload payload = createPayload(phone, "Não entendi nada dessa questão, me explica?");

        chatbotService.processIncomingMessage(payload);

        verify(geminiAiService).generateAcademicTutorExplanation(eq(student), eq(question), contains("me explica"));
        verify(messageSender).sendTextMessage(eq(phone), contains("Tutor Acadêmico (IA):"));
    }

    private UaiZapWebhookPayload createPayload(String phone, String text) {
        return UaiZapWebhookPayload.builder()
                .event("messages")
                .message(UaiZapWebhookPayload.UazapiMessage.builder()
                        .id("MSG-123456")
                        .sender_pn(phone)
                        .senderName("Aluno Teste")
                        .text(text)
                        .build())
                .build();
    }
}
