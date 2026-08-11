package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.I18nConfig;
import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.*;
import br.edu.unipam.tcc.repository.InteractionLogRepository;
import br.edu.unipam.tcc.repository.ReviewScheduleRepository;
import br.edu.unipam.tcc.repository.SpecialtyRepository;
import br.edu.unipam.tcc.repository.StudentRepository;
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
    @DisplayName("Para novo estudante, deve realizar cadastro e enviar mensagem de boas-vindas i18n")
    void shouldRegisterNewStudent() {
        String phone = "5534999999999";
        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());

        UaiZapWebhookPayload payload = createPayload(phone, "Olá");

        chatbotService.processIncomingMessage(payload);

        verify(studentRepository).save(any(Student.class));
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Bem-vindo(a) ao *Bot de Repetição Espaçada do Internato");
    }

    @Test
    @DisplayName("Deve responder à mensagem de texto natural 'ajuda' com texto i18n (Caminho Rápido)")
    void shouldRespondToNaturalHelpWord() {
        String phone = "5534999999999";
        Student student = Student.builder().phoneNumber(phone).fullName("Dr. Lucas").build();
        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));

        UaiZapWebhookPayload payload = createPayload(phone, "Ajuda");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("CENTRAL DE AJUDA - BOT MMEEBB");
        verifyNoInteractions(geminiAiService); // Garante que foi processado no fast-path sem gastar IA
    }

    @Test
    @DisplayName("Deve responder à mensagem de texto natural 'desempenho' com cálculo de acurácia i18n (Caminho Rápido)")
    void shouldRespondToNaturalStatsWord() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Dra. Paula").internPeriod(10).build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(studentId)).thenReturn(List.of(
                InteractionLog.builder().isCorrect(true).build(),
                InteractionLog.builder().isCorrect(true).build(),
                InteractionLog.builder().isCorrect(false).build()
        ));
        when(reviewScheduleRepository.findByStudentId(studentId)).thenReturn(List.of(
                ReviewSchedule.builder().id(1L).build()
        ));

        UaiZapWebhookPayload payload = createPayload(phone, "desempenho");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("SEU DESEMPENHO NO INTERNATO MÉDICO");
        assertThat(msgCaptor.getValue()).contains("Total de Questões Respondidas: *3*");
        assertThat(msgCaptor.getValue()).contains("Taxa de Retenção / Precisão: *66,7%*");
        verifyNoInteractions(geminiAiService);
    }

    @Test
    @DisplayName("Deve processar resposta correta de questão, avançar n no MMEEBB e enviar feedback i18n")
    void shouldProcessCorrectQuestionAnswer() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Dr. Pedro").build();

        Question question = Question.builder()
                .id(1L)
                .statement("Tratamento de Choque Séptico")
                .clinicalExplanation("Cristaloides 30ml/kg e antibiótico na 1ª hora.")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Cristaloides + ATB").isCorrect(true).build(),
                        QuestionOption.builder().letter('B').optionText("Apenas repouso").isCorrect(false).build()
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
                .thenReturn(List.of()); // Sem mais pendências após responder

        when(mmeebbEngineService.processReviewFeedback(eq(0), eq(0), eq(true), any(LocalDate.class)))
                .thenReturn(MmeebbCalculationResult.builder()
                        .nIndex(1)
                        .intervalDays(2)
                        .consecutiveCorrect(1)
                        .nextDueDate(LocalDate.now().plusDays(2))
                        .build());

        UaiZapWebhookPayload payload = createPayload(phone, "Alternativa A");

        chatbotService.processIncomingMessage(payload);

        verify(reviewScheduleRepository).save(schedule);
        assertThat(schedule.getNIndex()).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo("COMPLETED");

        verify(interactionLogRepository).save(any(InteractionLog.class));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender, atLeastOnce()).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getAllValues().get(0)).contains("PARABÉNS! RESPOSTA CORRETA!");
    }

    @Test
    @DisplayName("Deve acionar o Modo Tutor Clínico do Gemini quando o aluno enviar dúvida sobre a questão recente")
    void shouldTriggerClinicalTutorModeOnRecentDoubt() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Dra. Beatriz").build();

        Question question = Question.builder()
                .id(5L)
                .statement("Crise asmática grave em pediatria")
                .clinicalExplanation("Uso imediato de beta-2 agonista inalatório.")
                .build();

        InteractionLog recentLog = InteractionLog.builder()
                .student(student)
                .question(question)
                .answeredAt(OffsetDateTime.now().minusMinutes(2))
                .build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId)).thenReturn(List.of());
        when(interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(studentId)).thenReturn(List.of(recentLog));

        when(geminiAiService.generateClinicalTutorExplanation(eq("Dra. Beatriz"), anyString(), anyString(), anyString()))
                .thenReturn("Na crise de asma grave, o salbutamol atua relaxando a musculatura lisa bronquial rapidamente.");

        UaiZapWebhookPayload payload = createPayload(phone, "Não entendi por que não posso usar corticoide isolado de início?");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Tutor Clínico (IA):");
        assertThat(msgCaptor.getValue()).contains("salbutamol atua relaxando");
    }

    @Test
    @DisplayName("Deve delegar mensagem aberta para o Gemini e responder com mensagem conversacional de estudo")
    void shouldDelegateOpenEndedMessageToGemini() {
        UUID studentId = UUID.randomUUID();
        String phone = "5534999999999";
        Student student = Student.builder().id(studentId).phoneNumber(phone).fullName("Dr. Carlos").build();

        when(studentRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(student));
        when(reviewScheduleRepository.findCurrentlyAwaitingAnswer(studentId)).thenReturn(List.of());
        when(interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(studentId)).thenReturn(List.of());
        when(specialtyRepository.findAll()).thenReturn(List.of(
                Specialty.builder().code("CARDIOLOGIA").name("Cardiologia").build()
        ));

        when(geminiAiService.analyzeMessage(eq("Dr. Carlos"), anyString(), anyList()))
                .thenReturn(AiIntentResult.builder()
                        .intent(AiIntentResult.IntentType.GENERAL_CHAT)
                        .responseMessage("Bons estudos no plantão, Dr. Carlos! Estou pronto para enviar revisões.")
                        .handledByAi(true)
                        .build());

        UaiZapWebhookPayload payload = createPayload(phone, "Bom dia bot, hoje estou no plantão do internato");

        chatbotService.processIncomingMessage(payload);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendTextMessage(eq(phone), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Bons estudos no plantão, Dr. Carlos!");
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
