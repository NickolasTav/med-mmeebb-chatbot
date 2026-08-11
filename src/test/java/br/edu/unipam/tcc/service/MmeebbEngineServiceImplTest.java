package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.service.impl.MmeebbEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MmeebbEngineServiceImplTest {

    private MmeebbEngineServiceImpl engineService;

    @BeforeEach
    void setUp() {
        engineService = new MmeebbEngineServiceImpl();
    }

    @Test
    @DisplayName("Deve calcular corretamente a progressão geométrica em acertos sucessivos (2^0 até 2^3)")
    void shouldProgressGeometricallyOnSuccess() {
        LocalDate base = LocalDate.of(2026, 8, 11);

        // n=0 -> n=1 (2 dias)
        MmeebbCalculationResult r1 = engineService.processReviewFeedback(0, 0, true, base);
        assertThat(r1.getNIndex()).isEqualTo(1);
        assertThat(r1.getIntervalDays()).isEqualTo(2);
        assertThat(r1.getNextDueDate()).isEqualTo(base.plusDays(2));
        assertThat(r1.getConsecutiveCorrect()).isEqualTo(1);

        // n=1 -> n=2 (4 dias)
        MmeebbCalculationResult r2 = engineService.processReviewFeedback(1, 1, true, base);
        assertThat(r2.getNIndex()).isEqualTo(2);
        assertThat(r2.getIntervalDays()).isEqualTo(4);

        // n=2 -> n=3 (8 dias)
        MmeebbCalculationResult r3 = engineService.processReviewFeedback(2, 2, true, base);
        assertThat(r3.getNIndex()).isEqualTo(3);
        assertThat(r3.getIntervalDays()).isEqualTo(8);
    }

    @Test
    @DisplayName("Deve resetar para n=0 e 1 dia em caso de erro")
    void shouldResetToZeroOnError() {
        LocalDate base = LocalDate.of(2026, 8, 11);

        MmeebbCalculationResult r = engineService.processReviewFeedback(5, 5, false, base);
        assertThat(r.getNIndex()).isEqualTo(0);
        assertThat(r.getIntervalDays()).isEqualTo(1);
        assertThat(r.getNextDueDate()).isEqualTo(base.plusDays(1));
        assertThat(r.getConsecutiveCorrect()).isZero();
    }

    @Test
    @DisplayName("Não deve ultrapassar o limite n=13 (8192 dias)")
    void shouldNotExceedMaxN() {
        LocalDate base = LocalDate.of(2026, 8, 11);

        MmeebbCalculationResult r = engineService.processReviewFeedback(13, 13, true, base);
        assertThat(r.getNIndex()).isEqualTo(13);
        assertThat(r.getIntervalDays()).isEqualTo(8192);
    }
}
