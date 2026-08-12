package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.*;
import br.edu.unipam.tcc.repository.InteractionLogRepository;
import br.edu.unipam.tcc.repository.ReviewScheduleRepository;
import br.edu.unipam.tcc.repository.SpecialtyRepository;
import br.edu.unipam.tcc.repository.StudentRepository;
import br.edu.unipam.tcc.service.ChatbotService;
import br.edu.unipam.tcc.service.GeminiAiService;
import br.edu.unipam.tcc.service.MessageService;
import br.edu.unipam.tcc.service.MmeebbEngineService;
import br.edu.unipam.tcc.service.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private final StudentRepository studentRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final SpecialtyRepository specialtyRepository;
    private final MmeebbEngineService mmeebbEngineService;
    private final WhatsAppMessageSender messageSender;
    private final MessageService messageService;
    private final GeminiAiService geminiAiService;
    private final br.edu.unipam.tcc.service.AppMetricsService appMetricsService;

    @Override
    @Transactional
    public void processIncomingMessage(UaiZapWebhookPayload payload) {
        if (payload == null) {
            return;
        }

        String phone = payload.extractSenderPhone();
        String messageText = payload.extractMessageText();

        if (phone == null || phone.isBlank() || messageText == null || messageText.isBlank()) {
            return;
        }

        String messageId = payload.extractMessageId();

        br.edu.unipam.tcc.config.CorrelationMdcHelper.setContext(phone, messageId, "PROCESS_MESSAGE");
        try {
            log.info("Processando mensagem do WhatsApp [{}]: '{}'", phone, messageText);
            appMetricsService.recordWhatsAppMessage("INBOUND", "TEXT");

            Optional<Student> studentOpt = studentRepository.findByPhoneNumber(phone);
            if (studentOpt.isEmpty()) {
                handleStudentRegistration(phone, payload);
                return;
            }

            Student student = studentOpt.get();
            String normalized = normalize(messageText);

        // =========================================================================
        // CAMINHO RÁPIDO DETERMINÍSTICO (Custo Zero & Latência < 50ms)
        // =========================================================================
        if (isHelpIntent(normalized)) {
            sendHelpMessage(phone);
            return;
        }

        if (isStatsIntent(normalized)) {
            sendPerformanceStats(student);
            return;
        }

        if (isReviewIntent(normalized)) {
            handleManualReviewTrigger(student);
            return;
        }

        // Se o estudante possui uma questão ativa aguardando resposta
        List<ReviewSchedule> pendingSchedules = reviewScheduleRepository.findCurrentlyAwaitingAnswer(student.getId());
        if (!pendingSchedules.isEmpty()) {
            String cleanAnswer = messageText.toUpperCase().replaceAll("[^A-E]", "");
            if (!cleanAnswer.isEmpty()) {
                handleQuestionAnswer(student, pendingSchedules.get(0), messageText);
                return;
            }
        }

        // =========================================================================
        // CAMINHO COGNITIVO COM GOOGLE GEMINI (Padrão A: Híbrido)
        // =========================================================================
        handleHybridAiMessage(student, messageText);
        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }

    private void handleHybridAiMessage(Student student, String messageText) {
        String phone = student.getPhoneNumber();

        // 1. Verifica se é uma dúvida sobre a última questão respondida recentemente (Modo Tutor Clínico)
        List<InteractionLog> recentLogs = interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(student.getId());
        if (!recentLogs.isEmpty()) {
            InteractionLog lastInteraction = recentLogs.get(0);
            if (lastInteraction.getAnsweredAt() != null
                    && lastInteraction.getAnsweredAt().isAfter(OffsetDateTime.now().minusMinutes(15))
                    && isClinicalDoubtText(messageText)) {

                Question question = lastInteraction.getQuestion();
                appMetricsService.recordAiInteraction("CLINICAL_TUTOR", false);
                String tutorExplanation = geminiAiService.generateClinicalTutorExplanation(
                        student.getFullName(),
                        question.getStatement(),
                        question.getClinicalExplanation(),
                        messageText
                );

                messageSender.sendTextMessage(phone, "🩺 *Tutor Clínico (IA):*\n" + tutorExplanation);
                return;
            }
        }

        // 2. Classificação de Intenção e NLU via Gemini
        List<String> specialties = specialtyRepository.findAll().stream()
                .map(Specialty::getCode)
                .toList();

        AiIntentResult aiResult = geminiAiService.analyzeMessage(student.getFullName(), messageText, specialties);
        appMetricsService.recordAiInteraction("INTENT_CLASSIFIER", !aiResult.isHandledByAi());

        if (aiResult.isHandledByAi()) {
            switch (aiResult.getIntent()) {
                case REQUEST_REVIEW -> {
                    if (aiResult.getResponseMessage() != null && !aiResult.getResponseMessage().isBlank()) {
                        messageSender.sendTextMessage(phone, aiResult.getResponseMessage());
                    }
                    handleManualReviewTrigger(student);
                    return;
                }
                case STATS -> {
                    sendPerformanceStats(student);
                    return;
                }
                case HELP -> {
                    sendHelpMessage(phone);
                    return;
                }
                case EXPLAIN_CONCEPT, GENERAL_CHAT -> {
                    messageSender.sendTextMessage(phone, aiResult.getResponseMessage());
                    return;
                }
                default -> {
                    // Fallthrough para o menu padrão
                }
            }
        }

        // 3. Fallback Gracioso / Menu Principal
        sendMainMenuMessage(student);
    }

    private boolean isClinicalDoubtText(String text) {
        String n = normalize(text);
        return n.contains("por que") || n.contains("porque") || n.contains("pq")
                || n.contains("nao entendi") || n.contains("duvida") || n.contains("explica")
                || n.contains("gabarito") || n.contains("justificativa") || n.contains("fisiopatologia");
    }

    private boolean isHelpIntent(String text) {
        return text.equals("ajuda") || text.equals("/ajuda") || text.equals("!ajuda")
                || text.equals("help") || text.equals("/help")
                || text.equals("socorro") || text.equals("como funciona")
                || text.equals("menu") || text.equals("opcoes") || text.equals("duvida");
    }

    private boolean isStatsIntent(String text) {
        return text.equals("stats") || text.equals("/stats") || text.equals("!stats")
                || text.equals("desempenho") || text.equals("meu desempenho")
                || text.equals("estatisticas") || text.equals("como estou")
                || text.equals("progresso") || text.equals("meu progresso")
                || text.equals("acertos") || text.equals("relatorio");
    }

    private boolean isReviewIntent(String text) {
        return text.equals("revisar") || text.equals("/revisar") || text.equals("!revisar")
                || text.equals("revisao") || text.equals("estudar") || text.equals("bora estudar")
                || text.equals("questao") || text.equals("questoes")
                || text.equals("quero revisar") || text.equals("comecar")
                || text.equals("simulado") || text.equals("quiz");
    }

    private String normalize(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", ""); // Remove acentos
    }

    private void handleStudentRegistration(String phone, UaiZapWebhookPayload payload) {
        String pushName = payload.extractSenderName();

        Student newStudent = Student.builder()
                .phoneNumber(phone)
                .fullName(pushName)
                .internPeriod(9)
                .preferredStudyTime(LocalTime.of(8, 0))
                .active(true)
                .build();
        studentRepository.save(newStudent);

        String welcomeMsg = messageService.getMessage("bot.welcome");
        messageSender.sendTextMessage(phone, welcomeMsg);
    }

    private void handleQuestionAnswer(Student student, ReviewSchedule currentSchedule, String incomingMessage) {
        String phone = student.getPhoneNumber();
        Question question = currentSchedule.getQuestion();

        String cleanAnswer = incomingMessage.toUpperCase().replaceAll("[^A-E]", "");
        if (cleanAnswer.isEmpty()) {
            String invalidMsg = messageService.getMessage("bot.answer.invalid");
            messageSender.sendTextMessage(phone, invalidMsg);
            return;
        }

        char selectedLetter = cleanAnswer.charAt(0);
        Optional<QuestionOption> correctOptionOpt = question.getOptions().stream()
                .filter(QuestionOption::getIsCorrect)
                .findFirst();

        char correctLetter = correctOptionOpt.map(QuestionOption::getLetter).orElse('A');
        boolean isCorrect = (selectedLetter == correctLetter);

        // Atualização no algoritmo MMEEBB
        MmeebbCalculationResult result = mmeebbEngineService.processReviewFeedback(
                currentSchedule.getNIndex(),
                currentSchedule.getConsecutiveCorrect(),
                isCorrect,
                LocalDate.now()
        );

        String specialtyCode = (question.getTopic() != null && question.getTopic().getSpecialty() != null)
                ? question.getTopic().getSpecialty().getCode()
                : "GERAL";
        appMetricsService.recordReviewAnswer(isCorrect, specialtyCode);

        currentSchedule.setNIndex(result.getNIndex());
        currentSchedule.setIntervalDays(result.getIntervalDays());
        currentSchedule.setConsecutiveCorrect(result.getConsecutiveCorrect());
        currentSchedule.setRepetitionCount(currentSchedule.getRepetitionCount() + 1);
        currentSchedule.setLastReviewedAt(OffsetDateTime.now());
        currentSchedule.setNextDueDate(result.getNextDueDate());
        currentSchedule.setStatus("COMPLETED");

        reviewScheduleRepository.save(currentSchedule);

        // Log da interação
        InteractionLog logEntry = InteractionLog.builder()
                .student(student)
                .question(question)
                .reviewSchedule(currentSchedule)
                .rawMessage(incomingMessage)
                .selectedOption(String.valueOf(selectedLetter))
                .isCorrect(isCorrect)
                .answeredAt(OffsetDateTime.now())
                .build();
        interactionLogRepository.save(logEntry);

        // Montagem do feedback clínico via i18n
        String feedback;
        if (isCorrect) {
            feedback = messageService.getMessage(
                    "bot.answer.correct",
                    selectedLetter,
                    question.getClinicalExplanation(),
                    result.getNIndex(),
                    result.getIntervalDays()
            );
        } else {
            feedback = messageService.getMessage(
                    "bot.answer.incorrect",
                    selectedLetter,
                    correctLetter,
                    question.getClinicalExplanation()
            );
        }

        messageSender.sendTextMessage(phone, feedback);

        // Verifica se há mais questões pendentes para disparar
        List<ReviewSchedule> remaining = reviewScheduleRepository.findCurrentlyAwaitingAnswer(student.getId());
        if (!remaining.isEmpty()) {
            ReviewSchedule nextSchedule = remaining.get(0);
            messageSender.sendInteractiveQuestion(phone, nextSchedule.getQuestion());
        } else {
            String completedMsg = messageService.getMessage("bot.review.completed");
            messageSender.sendTextMessage(phone, completedMsg);
        }
    }

    private void handleManualReviewTrigger(Student student) {
        String phone = student.getPhoneNumber();
        List<ReviewSchedule> pendingSchedules = reviewScheduleRepository.findCurrentlyAwaitingAnswer(student.getId());

        if (!pendingSchedules.isEmpty()) {
            ReviewSchedule currentSchedule = pendingSchedules.get(0);
            messageSender.sendInteractiveQuestion(phone, currentSchedule.getQuestion());
        } else {
            String noneMsg = messageService.getMessage("bot.review.none", student.getFullName());
            messageSender.sendTextMessage(phone, noneMsg);
        }
    }

    private void sendPerformanceStats(Student student) {
        String phone = student.getPhoneNumber();
        List<InteractionLog> logs = interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(student.getId());
        List<ReviewSchedule> schedules = reviewScheduleRepository.findByStudentId(student.getId());

        int totalAnswered = logs.size();
        long correctCount = logs.stream().filter(InteractionLog::getIsCorrect).count();
        double accuracyRate = totalAnswered > 0 ? ((double) correctCount / totalAnswered) * 100.0 : 0.0;

        String statsMsg = messageService.getMessage(
                "bot.stats.title",
                student.getFullName(),
                student.getInternPeriod(),
                totalAnswered,
                correctCount,
                String.format(messageService.getCurrentLocale(), "%.1f", accuracyRate),
                schedules.size()
        );

        messageSender.sendTextMessage(phone, statsMsg);
    }

    private void sendHelpMessage(String phone) {
        String helpText = messageService.getMessage("bot.help");
        messageSender.sendTextMessage(phone, helpText);
    }

    private void sendMainMenuMessage(Student student) {
        String menuMsg = messageService.getMessage("bot.menu", student.getFullName());
        messageSender.sendTextMessage(student.getPhoneNumber(), menuMsg);
    }

    @Override
    public void sendWhatsAppMessage(String phoneNumber, String text) {
        messageSender.sendTextMessage(phoneNumber, text);
    }
}
