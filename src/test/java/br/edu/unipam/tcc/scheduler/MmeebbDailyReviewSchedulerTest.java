package br.edu.unipam.tcc.scheduler;

import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.QuestionOption;
import br.edu.unipam.tcc.entity.ReviewSchedule;
import br.edu.unipam.tcc.entity.Student;
import br.edu.unipam.tcc.repository.ReviewScheduleRepository;
import br.edu.unipam.tcc.service.ReviewSchedulerService;
import br.edu.unipam.tcc.service.WhatsAppMessageSender;
import br.edu.unipam.tcc.service.impl.ReviewSchedulerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MmeebbDailyReviewSchedulerTest {

    @Mock
    private ReviewScheduleRepository scheduleRepository;

    @Mock
    private WhatsAppMessageSender messageSender;

    @Mock
    private ReviewSchedulerService reviewSchedulerService;

    private ReviewSchedulerServiceImpl schedulerService;
    private MmeebbDailyReviewScheduler schedulerComponent;

    @BeforeEach
    void setUp() {
        schedulerService = new ReviewSchedulerServiceImpl(scheduleRepository, messageSender);
        schedulerComponent = new MmeebbDailyReviewScheduler(reviewSchedulerService);
    }

    @Test
    @DisplayName("ReviewSchedulerService deve encontrar revisões pendentes do dia e disparar via WhatsApp")
    void shouldFindPendingReviewsAndDispatch() {
        Question question = Question.builder()
                .id(1L)
                .statement("Paciente com sepse...")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Iniciar Ceftriaxona").build()
                ))
                .build();

        Student student = Student.builder()
                .id(UUID.randomUUID())
                .phoneNumber("5534999999999")
                .fullName("Dr. Lucas")
                .active(true)
                .build();

        ReviewSchedule schedule = ReviewSchedule.builder()
                .id(10L)
                .student(student)
                .question(question)
                .nIndex(0)
                .status("PENDING")
                .nextDueDate(LocalDate.now())
                .build();

        when(scheduleRepository.findPendingReviewsForDate(any(LocalDate.class))).thenReturn(List.of(schedule));

        schedulerService.executeDailyScheduledDispatches();

        assertEquals("NOTIFIED", schedule.getStatus());
        verify(scheduleRepository).save(schedule);
        verify(messageSender).sendInteractiveQuestion(eq("5534999999999"), eq(question));
    }

    @Test
    @DisplayName("Scheduler component deve delegar para a interface ReviewSchedulerService")
    void shouldDelegateToService() {
        schedulerComponent.executeDailyReviewDispatch();
        verify(reviewSchedulerService).executeDailyScheduledDispatches();
    }
}
