package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.I18nConfig;
import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
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
                appMetricsService
        );
    }

    @Test
    @DisplayName("Para novo estudante, deve realizar cadastro associando ao curso e enviar mensagem de boas-vindas i18n")
    void shouldRegisterNewStudentWithCourse() {
        String phone = "5534999999999";
        Course medCourse = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(courseRepository.findByCodeIgnoreCase("MEDICINA")).thenReturn(Optional.of(medCourse));

        UaiZapWebhookPayload payload = createPayload(phone, "Olá");

        chatbotService.processIncomingMessage(payload);

        verify(studentRepository).save(any(Student.class));
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Bem-vindo(a) ao *Bot MMEEBB de Repetição Espaçada (Medicina - UNIPAM)*");
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
    @DisplayName("Deve responder ao comando '/stats' com métricas acadêmicas por curso e período")
    void shouldRespondToStatsCommandWithAcademicMetrics() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Course lawCourse = Course.builder().name("Direito").build();
        Student student = Student.builder()
                .id(studentId)
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
        assertThat(msgCaptor.getValue()).contains("Curso:* Direito (5º Período)");
        assertThat(msgCaptor.getValue()).contains("Total de Questões Respondidas: *3*");
        assertThat(msgCaptor.getValue()).contains("Taxa de Retenção / Precisão: *66,7%*");
        verifyNoInteractions(geminiAiService);
    }

    @Test
    @DisplayName("Deve processar resposta atômica 'B' de questão, avançar n no MMEEBB e enviar feedback")
    void shouldProcessAtomicQuestionAnswer() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Pedro").build();

        Question question = Question.builder()
                .id(1L)
                .statement("Tratamento de Choque Séptico")
                .clinicalExplanation("Cristaloides 30ml/kg e antibiótico na 1ª hora.")
                .explanation("Cristaloides 30ml/kg e antibiótico na 1ª hora.")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Apenas repouso").isCorrect(false).build(),
                        QuestionOption.builder().letter('B').optionText("Cristaloides + ATB").isCorrect(true).build()
                ))
                .build();

        ReviewSchedule schedule = ReviewSchedule.builder()
                .id(10L)
                .student(student)
                .question(question)
                .nIndex(0)
                .consecutiveCorrect(0)
                .repetitionCount(0)
                .status("NOTIFIED")
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId))
                .thenReturn(List.of(schedule))
                .thenReturn(List.of());

        when(mmeebbEngineService.processReviewFeedback(eq(0), eq(0), eq(true), any(LocalDate.class)))
                .thenReturn(MmeebbCalculationResult.builder()
                        .nIndex(1)
                        .intervalDays(2)
                        .consecutiveCorrect(1)
                        .nextDueDate(LocalDate.now().plusDays(2))
                        .build());

        UaiZapWebhookPayload payload = createPayload(phone, "b");

        chatbotService.processIncomingMessage(payload);

        verify(reviewScheduleRepository).save(schedule);
        assertThat(schedule.getNIndex()).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo("COMPLETED");

        verify(interactionLogRepository).save(any(InteractionLog.class));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender, atLeastOnce()).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getAllValues().get(0)).contains("PARABÉNS! RESPOSTA CORRETA! (Alternativa B)");
    }

    @Test
    @DisplayName("Deve usar o Gemini para extrair resposta discursiva e pontuar no MMEEBB")
    void shouldExtractDiscursiveAnswerViaAiAndScore() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Mariana").build();

        Question question = Question.builder()
                .id(2L)
                .statement("Qual remédio constitucional é cabível?")
                .explanation("O Habeas Data destina-se a proteger dados pessoais.")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Mandado de Segurança").isCorrect(false).build(),
                        QuestionOption.builder().letter('B').optionText("Habeas Data").isCorrect(true).build()
                ))
                .build();

        ReviewSchedule schedule = ReviewSchedule.builder()
                .id(20L)
                .student(student)
                .question(question)
                .nIndex(1)
                .consecutiveCorrect(1)
                .status("NOTIFIED")
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId))
                .thenReturn(List.of(schedule))
                .thenReturn(List.of());

        when(geminiAiService.analyzeMessage(eq(student), anyString(), eq(question), any(), anyList()))
                .thenReturn(AiIntentResult.builder()
                        .intent(AiIntentResult.IntentType.ANSWER_ACTIVE_QUESTION)
                        .extractedOption('B')
                        .handledByAi(true)
                        .build());

        when(mmeebbEngineService.processReviewFeedback(eq(1), eq(1), eq(true), any(LocalDate.class)))
                .thenReturn(MmeebbCalculationResult.builder()
                        .nIndex(2)
                        .intervalDays(4)
                        .consecutiveCorrect(2)
                        .nextDueDate(LocalDate.now().plusDays(4))
                        .build());

        UaiZapWebhookPayload payload = createPayload(phone, "Acredito com certeza que a opção correta seja a letra B!");

        chatbotService.processIncomingMessage(payload);

        verify(reviewScheduleRepository).save(schedule);
        assertThat(schedule.getNIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve acionar o Modo Tutor Acadêmico quando o aluno tiver dúvida em linguagem natural")
    void shouldTriggerAcademicTutorModeOnNaturalDoubt() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Beatriz").build();

        Question question = Question.builder()
                .id(5L)
                .statement("Crise asmática grave")
                .explanation("Uso imediato de beta-2 agonista inalatório.")
                .build();

        InteractionLog recentLog = InteractionLog.builder()
                .student(student)
                .question(question)
                .answeredAt(OffsetDateTime.now().minusMinutes(2))
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId)).thenReturn(List.of());
        when(interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(studentId)).thenReturn(List.of(recentLog));

        when(geminiAiService.analyzeMessage(eq(student), anyString(), isNull(), eq(recentLog), anyList()))
                .thenReturn(AiIntentResult.builder()
                        .intent(AiIntentResult.IntentType.EXPLAIN_CONCEPT)
                        .handledByAi(true)
                        .build());

        when(geminiAiService.generateAcademicTutorExplanation(eq(student), eq(question), anyString()))
                .thenReturn("Na crise de asma grave, o salbutamol atua relaxando a musculatura lisa bronquial rapidamente.");

        UaiZapWebhookPayload payload = createPayload(phone, "Não entendi nada dessa questão, me explica?");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Tutor Acadêmico (IA):");
        assertThat(msgCaptor.getValue()).contains("salbutamol atua relaxando");
    }

    private UaiZapWebhookPayload createPayload(String phone, String text) {
        return UaiZapWebhookPayload.builder()
                .event("messages.upsert")
                .data(new UaiZapWebhookPayload.MessageData(
                        new UaiZapWebhookPayload.MessageKey(phone + "@s.whatsapp.net", false, "ID_123"),
                        "Student",
                        "conversation",
                        new UaiZapWebhookPayload.MessageContent(text, null),
                        System.currentTimeMillis()
                ))
                .build();
    }
}
