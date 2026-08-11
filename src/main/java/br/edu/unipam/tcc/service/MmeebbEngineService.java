package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.MmeebbCalculationResult;

import java.time.LocalDate;

public interface MmeebbEngineService {

    MmeebbCalculationResult processReviewFeedback(
            int currentNIndex,
            int consecutiveCorrect,
            boolean isCorrect,
            LocalDate baseDate
    );

    int calculateIntervalDays(int nIndex);
}
