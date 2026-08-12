package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.UnipamStudentProfileDto;
import br.edu.unipam.tcc.entity.Course;
import br.edu.unipam.tcc.entity.UnipamAcademicRecord;
import br.edu.unipam.tcc.exception.ResourceNotFoundException;
import br.edu.unipam.tcc.repository.UnipamAcademicRecordRepository;
import br.edu.unipam.tcc.service.impl.UnipamAcademicIntegrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnipamAcademicIntegrationServiceImplTest {

    @Mock
    private UnipamAcademicRecordRepository academicRecordRepository;

    private UnipamAcademicIntegrationService integrationService;

    @BeforeEach
    void setUp() {
        integrationService = new UnipamAcademicIntegrationServiceImpl(academicRecordRepository);
    }

    @Test
    @DisplayName("Deve encontrar estudante na UNIPAM por telefone com match exato")
    void shouldFindStudentByExactPhoneNumber() {
        String phone = "5534999999999";
        Course course = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamAcademicRecord record = UnipamAcademicRecord.builder()
                .id(1L)
                .ra("23000388")
                .fullName("Níckolas Tavares")
                .phoneNumber(phone)
                .course(course)
                .academicPeriod(9)
                .email("nickolas.tavares@unipam.edu.br")
                .active(true)
                .build();

        when(academicRecordRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(record));

        Optional<UnipamStudentProfileDto> result = integrationService.findStudentByPhoneNumber(phone);

        assertThat(result).isPresent();
        assertThat(result.get().getRa()).isEqualTo("23000388");
        assertThat(result.get().getFullName()).isEqualTo("Níckolas Tavares");
        assertThat(result.get().getCourseCode()).isEqualTo("MEDICINA");
        assertThat(result.get().getAcademicPeriod()).isEqualTo(9);
    }

    @Test
    @DisplayName("Deve encontrar estudante na UNIPAM por sufixo de dígitos do telefone")
    void shouldFindStudentByPhoneSuffixDigits() {
        String phoneIncoming = "+55 (34) 99999-9999";
        Course course = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamAcademicRecord record = UnipamAcademicRecord.builder()
                .id(1L)
                .ra("23000388")
                .fullName("Níckolas Tavares")
                .phoneNumber("5534999999999")
                .course(course)
                .academicPeriod(9)
                .active(true)
                .build();

        when(academicRecordRepository.findByPhoneNumber(phoneIncoming)).thenReturn(Optional.empty());
        when(academicRecordRepository.findByPhoneNumber("5534999999999")).thenReturn(Optional.empty());
        when(academicRecordRepository.findAll()).thenReturn(List.of(record));

        Optional<UnipamStudentProfileDto> result = integrationService.findStudentByPhoneNumber(phoneIncoming);

        assertThat(result).isPresent();
        assertThat(result.get().getRa()).isEqualTo("23000388");
    }

    @Test
    @DisplayName("Deve retornar vazio quando telefone não existir na base UNIPAM")
    void shouldReturnEmptyWhenPhoneNotFound() {
        String phone = "5534900000000";
        when(academicRecordRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(academicRecordRepository.findByPhoneNumber("5534900000000")).thenReturn(Optional.empty());
        when(academicRecordRepository.findAll()).thenReturn(List.of());

        Optional<UnipamStudentProfileDto> result = integrationService.findStudentByPhoneNumber(phone);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve encontrar estudante na UNIPAM por RA institucional (ex: 23000388)")
    void shouldFindStudentByRa() {
        String ra = "23000388";
        Course course = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamAcademicRecord record = UnipamAcademicRecord.builder()
                .id(1L)
                .ra(ra)
                .fullName("Níckolas Tavares")
                .phoneNumber("5534999999999")
                .course(course)
                .academicPeriod(9)
                .active(true)
                .build();

        when(academicRecordRepository.findByRa(ra)).thenReturn(Optional.of(record));

        Optional<UnipamStudentProfileDto> result = integrationService.findStudentByRa(ra);

        assertThat(result).isPresent();
        assertThat(result.get().getRa()).isEqualTo("23000388");
        assertThat(result.get().getFullName()).isEqualTo("Níckolas Tavares");
    }

    @Test
    @DisplayName("Deve vincular novo número de WhatsApp ao RA institucional")
    void shouldLinkNewPhoneNumberToRa() {
        String ra = "23000388";
        String newPhone = "5534988776655";
        Course course = Course.builder().id(1L).code("MEDICINA").name("Medicina").build();
        UnipamAcademicRecord record = UnipamAcademicRecord.builder()
                .id(1L)
                .ra(ra)
                .fullName("Níckolas Tavares")
                .phoneNumber("5534999999999")
                .course(course)
                .academicPeriod(9)
                .active(true)
                .build();

        when(academicRecordRepository.findByRa(ra)).thenReturn(Optional.of(record));

        UnipamStudentProfileDto updated = integrationService.linkPhoneNumberToRa(ra, newPhone);

        assertThat(updated.getPhoneNumber()).isEqualTo(newPhone);
        verify(academicRecordRepository).save(record);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar vincular telefone a um RA inexistente")
    void shouldThrowExceptionWhenRaNotFoundForLinking() {
        when(academicRecordRepository.findByRa("99999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> integrationService.linkPhoneNumberToRa("99999999", "5534999999999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99999999");
    }
}
