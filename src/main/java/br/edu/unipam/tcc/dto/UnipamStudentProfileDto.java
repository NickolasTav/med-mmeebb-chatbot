package br.edu.unipam.tcc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnipamStudentProfileDto {
    private String ra;
    private String fullName;
    private String phoneNumber;
    private String courseCode;
    private String courseName;
    private Integer academicPeriod;
    private String email;
    private Boolean active;
}
