package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.entity.ReviewSchedule;
import br.edu.unipam.tcc.entity.Student;
import br.edu.unipam.tcc.repository.ReviewScheduleRepository;
import br.edu.unipam.tcc.service.ReviewSchedulerService;
import br.edu.unipam.tcc.service.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSchedulerServiceImpl implements ReviewSchedulerService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final WhatsAppMessageSender messageSender;

    @Override
    @Transactional
    public void executeDailyScheduledDispatches() {
        LocalDate today = LocalDate.now();
        log.info("Iniciando rotina de disparos diários de revisões MMEEBB para a data: {}", today);

        List<ReviewSchedule> pendingSchedules = reviewScheduleRepository.findPendingReviewsForDate(today);
        log.info("Total de revisões pendentes encontradas: {}", pendingSchedules.size());

        for (ReviewSchedule schedule : pendingSchedules) {
            try {
                Student student = schedule.getStudent();
                if (student == null || !Boolean.TRUE.equals(student.getActive())) {
                    continue;
                }

                // Envia a questão interativa via WhatsApp
                messageSender.sendInteractiveQuestion(student.getPhoneNumber(), schedule.getQuestion());

                schedule.setStatus("NOTIFIED");
                reviewScheduleRepository.save(schedule);
                log.info("Questão [{}] disparada para o estudante [{}]", schedule.getQuestion().getId(), student.getPhoneNumber());

            } catch (Exception e) {
                log.error("Falha ao disparar revisão ID [{}]: {}", schedule.getId(), e.getMessage(), e);
            }
        }

        log.info("Rotina diária de disparos MMEEBB finalizada com sucesso.");
    }
}
