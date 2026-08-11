package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.dto.MmeebbCalculationResult;
import br.edu.unipam.tcc.service.MmeebbEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class MmeebbEngineServiceImpl implements MmeebbEngineService {

    public static final int MIN_N = 0;
    public static final int MAX_N = 13;

    @Override
    public MmeebbCalculationResult processReviewFeedback(
            int currentNIndex,
            int consecutiveCorrect,
            boolean isCorrect,
            LocalDate baseDate
    ) {
        if (baseDate == null) {
            baseDate = LocalDate.now();
        }

        int nextN;
        int nextConsecutiveCorrect;

        if (isCorrect) {
            nextN = Math.min(currentNIndex + 1, MAX_N);
            nextConsecutiveCorrect = consecutiveCorrect + 1;
            log.info("MMEEBB: Feedback positivo -> n evoluiu de {} para {}, acertos consecutivos: {}",
                    currentNIndex, nextN, nextConsecutiveCorrect);
        } else {
            nextN = MIN_N;
            nextConsecutiveCorrect = 0;
            log.info("MMEEBB: Feedback negativo -> n resetado para {}, acertos consecutivos zerados", nextN);
        }

        int intervalDays = calculateIntervalDays(nextN);
        LocalDate nextDueDate = baseDate.plusDays(intervalDays);

        return MmeebbCalculationResult.builder()
                .nIndex(nextN)
                .intervalDays(intervalDays)
                .consecutiveCorrect(nextConsecutiveCorrect)
                .nextDueDate(nextDueDate)
                .build();
    }

    @Override
    public int calculateIntervalDays(int nIndex) {
        if (nIndex < MIN_N) {
            nIndex = MIN_N;
        }
        if (nIndex > MAX_N) {
            nIndex = MAX_N;
        }
        return (int) Math.pow(2, nIndex);
    }
}
