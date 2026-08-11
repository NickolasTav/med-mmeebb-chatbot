package br.edu.unipam.tcc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MmeebbCalculationResult implements Serializable {

    private Integer nIndex;
    private Integer intervalDays;
    private LocalDate nextDueDate;
    private Integer consecutiveCorrect;
}
