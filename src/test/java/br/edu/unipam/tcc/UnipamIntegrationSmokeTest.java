package br.edu.unipam.tcc;

import br.edu.unipam.tcc.dto.UnipamStudentProfileDto;
import br.edu.unipam.tcc.entity.Course;
import br.edu.unipam.tcc.entity.Student;
import br.edu.unipam.tcc.entity.UnipamAcademicRecord;
import br.edu.unipam.tcc.repository.CourseRepository;
import br.edu.unipam.tcc.repository.StudentRepository;
import br.edu.unipam.tcc.repository.UnipamAcademicRecordRepository;
import br.edu.unipam.tcc.service.UnipamAcademicIntegrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnipamIntegrationSmokeTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UnipamAcademicRecordRepository academicRecordRepository;

    @Autowired
    private UnipamAcademicIntegrationService unipamAcademicIntegrationService;

    @Test
    @DisplayName("Smoke Test: Valida identificação por RA institucional e integração com base acadêmica UNIPAM")
    void smokeTestUnipamRaAndAcademicIntegrationWiring() {
        // 1. Cria curso para teste
        Course medCourse = courseRepository.save(Course.builder()
                .code("MEDICINA")
                .name("Medicina")
                .tutorPersona("Você é um Preceptor Médico.")
                .build());

        // 2. Cria registro acadêmico institucional UNIPAM
        UnipamAcademicRecord record = academicRecordRepository.save(UnipamAcademicRecord.builder()
                .ra("23000388")
                .fullName("Níckolas Tavares do Nascimento")
                .phoneNumber("5534999999999")
                .course(medCourse)
                .academicPeriod(9)
                .email("nickolas.tavares@unipam.edu.br")
                .active(true)
                .build());

        assertThat(record.getId()).isNotNull();

        // 3. Testa consulta no serviço de integração institucional
        Optional<UnipamStudentProfileDto> profileOpt = unipamAcademicIntegrationService.findStudentByPhoneNumber("5534999999999");
        assertThat(profileOpt).isPresent();
        assertThat(profileOpt.get().getRa()).isEqualTo("23000388");
        assertThat(profileOpt.get().getFullName()).isEqualTo("Níckolas Tavares do Nascimento");
        assertThat(profileOpt.get().getCourseCode()).isEqualTo("MEDICINA");

        // 4. Salva estudante com RA e valida busca
        Student student = studentRepository.save(Student.builder()
                .ra("23000388")
                .phoneNumber("5534999999999")
                .fullName("Níckolas Tavares do Nascimento")
                .course(medCourse)
                .academicPeriod(9)
                .build());

        assertThat(student.getId()).isNotNull();

        Optional<Student> foundByRa = studentRepository.findByRa("23000388");
        assertThat(foundByRa).isPresent();
        assertThat(foundByRa.get().getFullName()).isEqualTo("Níckolas Tavares do Nascimento");
        assertThat(foundByRa.get().getRa()).isEqualTo("23000388");

        boolean exists = studentRepository.existsByRa("23000388");
        assertThat(exists).isTrue();
    }
}
