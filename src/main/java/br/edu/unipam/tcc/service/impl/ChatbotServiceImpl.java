package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;
import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.*;
import br.edu.unipam.tcc.repository.*;
import br.edu.unipam.tcc.service.ChatbotService;
import br.edu.unipam.tcc.service.GeminiAiService;
import br.edu.unipam.tcc.service.MessageService;
import br.edu.unipam.tcc.service.MmeebbEngineService;
import br.edu.unipam.tcc.service.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CourseRepository courseRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final SpecialtyRepository specialtyRepository;
    private final MmeebbEngineService mmeebbEngineService;
    private final WhatsAppMessageSender messageSender;
    private final MessageService messageService;
    private final GeminiAiService geminiAiService;
    private final br.edu.unipam.tcc.service.AppMetricsService appMetricsService;
    private final br.edu.unipam.tcc.service.UnipamAcademicIntegrationService unipamAcademicIntegrationService;

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
                handleStudentRegistration(phone, messageText, payload);
                return;
            }

            Student student = studentOpt.get();
            String trimmed = messageText.trim();

            // =========================================================================
            // 1. FAST-PATH DETERMINÍSTICO (Estritamente para comandos diretos e opções)
            // =========================================================================
            if (trimmed.equalsIgnoreCase("/ajuda") || trimmed.equalsIgnoreCase("/help")) {
                sendHelpMessage(phone);
                return;
            }

            if (trimmed.equalsIgnoreCase("/stats") || trimmed.equalsIgnoreCase("/desempenho")) {
                sendPerformanceStats(student);
                return;
            }

            if (trimmed.equalsIgnoreCase("/revisar") || trimmed.equalsIgnoreCase("/estudar")) {
                handleManualReviewTrigger(student);
                return;
            }

            // Se o estudante possui uma questão ativa aguardando resposta
            List<ReviewSchedule> pendingSchedules = reviewScheduleRepository.findCurrentlyAwaitingAnswer(student.getId());
            ReviewSchedule activeSchedule = pendingSchedules.isEmpty() ? null : pendingSchedules.get(0);

            // Fast-Path de resposta atômica (ex: "A", "b", "1", "2")
            if (activeSchedule != null && isAtomicOption(trimmed)) {
                char selectedLetter = mapToOptionLetter(trimmed);
                handleQuestionAnswer(student, activeSchedule, selectedLetter, messageText);
                return;
            }

            // =========================================================================
            // 2. CAMINHO COGNITIVO COM GOOGLE GEMINI (NLU Semântico Multi-Curso)
            // =========================================================================
            handleCognitiveAiMessage(student, messageText, activeSchedule);

        } finally {
            br.edu.unipam.tcc.config.CorrelationMdcHelper.clearContext();
        }
    }

    private boolean isAtomicOption(String text) {
        if (text == null || text.length() > 2) return false;
        String upper = text.toUpperCase().trim();
        return upper.matches("^[A-E]$") || upper.matches("^[1-5]$");
    }

    private char mapToOptionLetter(String text) {
        String clean = text.toUpperCase().trim();
        if (clean.matches("^[1-5]$")) {
            int num = Integer.parseInt(clean);
            return (char) ('A' + (num - 1));
        }
        return clean.charAt(0);
    }

    private void handleCognitiveAiMessage(Student student, String messageText, ReviewSchedule activeSchedule) {
        String phone = student.getPhoneNumber();
        Question activeQuestion = activeSchedule != null ? activeSchedule.getQuestion() : null;

        // Busca última interação recente (últimos 30 minutos)
        List<InteractionLog> recentLogs = interactionLogRepository.findByStudentIdOrderByAnsweredAtDesc(student.getId());
        InteractionLog lastInteraction = recentLogs.isEmpty() ? null : recentLogs.get(0);

        List<String> availableAreas = specialtyRepository.findAll().stream()
                .map(Specialty::getCode)
                .toList();

        // Análise semântica rica com contexto completo
        AiIntentResult aiResult = geminiAiService.analyzeMessage(
                student,
                messageText,
                activeQuestion,
                lastInteraction,
                availableAreas
        );

        appMetricsService.recordAiInteraction("INTENT_CLASSIFIER", !aiResult.isHandledByAi());

        if (aiResult.isHandledByAi()) {
            switch (aiResult.getIntent()) {
                case ANSWER_ACTIVE_QUESTION -> {
                    if (activeSchedule != null && aiResult.getExtractedOption() != null) {
                        handleQuestionAnswer(student, activeSchedule, aiResult.getExtractedOption(), messageText);
                        return;
                    }
                }
                case EXPLAIN_CONCEPT -> {
                    Question questionToExplain = activeQuestion != null
                            ? activeQuestion
                            : (lastInteraction != null ? lastInteraction.getQuestion() : null);

                    if (questionToExplain != null) {
                        appMetricsService.recordAiInteraction("ACADEMIC_TUTOR", false);
                        String tutorExplanation = geminiAiService.generateAcademicTutorExplanation(
                                student,
                                questionToExplain,
                                messageText
                        );
                        messageSender.sendTextMessage(phone, "🎓 *Tutor Acadêmico (IA):*\n" + tutorExplanation);
                        return;
                    } else if (aiResult.getResponseMessage() != null && !aiResult.getResponseMessage().isBlank()) {
                        messageSender.sendTextMessage(phone, aiResult.getResponseMessage());
                        return;
                    }
                }
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
                    if (aiResult.getResponseMessage() != null && !aiResult.getResponseMessage().isBlank()) {
                        messageSender.sendTextMessage(phone, aiResult.getResponseMessage());
                    } else {
                        sendHelpMessage(phone);
                    }
                    return;
                }
                case GENERAL_CHAT -> {
                    messageSender.sendTextMessage(phone, aiResult.getResponseMessage());
                    return;
                }
                default -> {
                    // Fallthrough para o menu padrão
                }
            }
        }

        // Fallback Gracioso / Menu Principal
        sendMainMenuMessage(student);
    }

    private void handleStudentRegistration(String phone, String messageText, UaiZapWebhookPayload payload) {
        // 1. Tenta identificar via número de WhatsApp cadastrado na UNIPAM
        Optional<br.edu.unipam.tcc.dto.UnipamStudentProfileDto> profileOpt = unipamAcademicIntegrationService.findStudentByPhoneNumber(phone);
        if (profileOpt.isPresent()) {
            registerStudentFromUnipamProfile(phone, profileOpt.get(), false);
            return;
        }

        // 2. Se o número não foi encontrado, verifica se a mensagem enviada é um RA válido (ex: 23000388)
        String trimmed = (messageText != null) ? messageText.trim() : "";
        if (isRaFormat(trimmed)) {
            Optional<br.edu.unipam.tcc.dto.UnipamStudentProfileDto> raProfileOpt = unipamAcademicIntegrationService.findStudentByRa(trimmed);
            if (raProfileOpt.isPresent()) {
                // Atualiza telefone na base UNIPAM e registra o estudante
                br.edu.unipam.tcc.dto.UnipamStudentProfileDto updatedProfile = unipamAcademicIntegrationService.linkPhoneNumberToRa(trimmed, phone);
                registerStudentFromUnipamProfile(phone, updatedProfile, true);
                return;
            } else {
                String invalidRaMsg = messageService.getMessage("bot.registration.ra_invalid", trimmed);
                messageSender.sendTextMessage(phone, invalidRaMsg);
                return;
            }
        }

        // 3. Telefone desconhecido e não é RA: Envia mensagem orientando a digitar o RA
        String notFoundMsg = messageService.getMessage("bot.registration.not_found");
        messageSender.sendTextMessage(phone, notFoundMsg);
    }

    private void registerStudentFromUnipamProfile(String phone, br.edu.unipam.tcc.dto.UnipamStudentProfileDto profile, boolean isManualLinking) {
        Course course = null;
        if (profile.getCourseCode() != null) {
            course = courseRepository.findByCodeIgnoreCase(profile.getCourseCode()).orElse(null);
        }
        if (course == null) {
            course = courseRepository.findByCodeIgnoreCase("MEDICINA")
                    .or(courseRepository::findFirstByActiveTrueOrderByIdAsc)
                    .orElse(null);
        }

        // Verifica se já existe Student com esse RA (caso esteja apenas trocando de telefone)
        Student student = studentRepository.findByRa(profile.getRa())
                .orElseGet(() -> Student.builder().ra(profile.getRa()).build());

        student.setPhoneNumber(phone);
        student.setFullName(profile.getFullName());
        student.setCourse(course);
        student.setAcademicPeriod(profile.getAcademicPeriod() != null ? profile.getAcademicPeriod() : 1);
        student.setInternPeriod(profile.getAcademicPeriod() != null ? profile.getAcademicPeriod() : 9);
        student.setPreferredStudyTime(LocalTime.of(8, 0));
        student.setActive(true);

        studentRepository.save(student);

        String courseName = course != null ? course.getName() : (profile.getCourseName() != null ? profile.getCourseName() : "Graduação");
        String welcomeMsg = messageService.getMessage("bot.welcome", student.getFullName(), courseName, student.getRa());

        if (isManualLinking) {
            String linkedHeader = messageService.getMessage("bot.registration.ra_linked", student.getRa());
            welcomeMsg = linkedHeader + "\n\n" + welcomeMsg;
        }

        messageSender.sendTextMessage(phone, welcomeMsg);
    }

    private boolean isRaFormat(String text) {
        if (text == null) return false;
        String clean = text.trim();
        return clean.matches("^\\d{6,12}$");
    }

    private void handleQuestionAnswer(Student student, ReviewSchedule currentSchedule, char selectedLetter, String incomingMessage) {
        String phone = student.getPhoneNumber();
        Question question = currentSchedule.getQuestion();

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

        // Montagem do feedback pedagógico via i18n
        String feedback;
        String explanation = question.getEffectiveExplanation();
        if (isCorrect) {
            feedback = messageService.getMessage(
                    "bot.answer.correct",
                    selectedLetter,
                    explanation,
                    result.getNIndex(),
                    result.getIntervalDays()
            );
        } else {
            feedback = messageService.getMessage(
                    "bot.answer.incorrect",
                    selectedLetter,
                    correctLetter,
                    explanation
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

        String raDisplay = student.getRa() != null ? student.getRa() : "N/D";
        String courseName = student.getCourse() != null ? student.getCourse().getName() : "Graduação";
        int period = student.getAcademicPeriod() != null ? student.getAcademicPeriod() : (student.getInternPeriod() != null ? student.getInternPeriod() : 1);

        String statsMsg = messageService.getMessage(
                "bot.stats.title",
                student.getFullName(),
                raDisplay,
                courseName,
                period,
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
