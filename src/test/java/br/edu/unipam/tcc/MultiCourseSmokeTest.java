package br.edu.unipam.tcc;

import br.edu.unipam.tcc.entity.Course;
import br.edu.unipam.tcc.entity.Specialty;
import br.edu.unipam.tcc.entity.Student;
import br.edu.unipam.tcc.repository.CourseRepository;
import br.edu.unipam.tcc.repository.SpecialtyRepository;
import br.edu.unipam.tcc.repository.StudentRepository;
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
class MultiCourseSmokeTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("Smoke Test: Valida inicialização, persistência e relacionamentos de múltiplos cursos (Medicina, Direito, TI)")
    void smokeTestMultiCourseBootstrapAndDataWiring() {
        // 1. Cria e persiste cursos dinâmicos
        Course medCourse = courseRepository.save(Course.builder()
                .code("MEDICINA")
                .name("Medicina")
                .tutorPersona("Você é um Preceptor e Tutor Médico especialista.")
                .build());

        Course lawCourse = courseRepository.save(Course.builder()
                .code("DIREITO")
                .name("Direito")
                .tutorPersona("Você é um Professor Jurista especialista.")
                .build());

        Course csCourse = courseRepository.save(Course.builder()
                .code("ENGENHARIA_SOFTWARE")
                .name("Engenharia de Software")
                .tutorPersona("Você é um Arquiteto de Software sênior.")
                .build());

        assertThat(medCourse.getId()).isNotNull();
        assertThat(lawCourse.getId()).isNotNull();
        assertThat(csCourse.getId()).isNotNull();

        // 2. Cria áreas de conhecimento / disciplinas associadas aos cursos
        Specialty pediatricDiscipline = specialtyRepository.save(Specialty.builder()
                .code("PEDIATRIA")
                .name("Pediatria")
                .course(medCourse)
                .build());

        Specialty lawDiscipline = specialtyRepository.save(Specialty.builder()
                .code("DIR_CONSTITUCIONAL")
                .name("Direito Constitucional")
                .course(lawCourse)
                .build());

        assertThat(pediatricDiscipline.getCourse().getCode()).isEqualTo("MEDICINA");
        assertThat(lawDiscipline.getCourse().getCode()).isEqualTo("DIREITO");

        // 3. Valida cadastro e consulta de estudantes em cursos distintos
        Student lawStudent = studentRepository.save(Student.builder()
                .phoneNumber("5534988887777")
                .fullName("Mariana Silva")
                .course(lawCourse)
                .academicPeriod(7)
                .build());

        Student medStudent = studentRepository.save(Student.builder()
                .phoneNumber("5534977776666")
                .fullName("Lucas Mendes")
                .course(medCourse)
                .academicPeriod(9)
                .build());

        Optional<Student> foundLawStudent = studentRepository.findByPhoneNumber("5534988887777");
        assertThat(foundLawStudent).isPresent();
        assertThat(foundLawStudent.get().getCourse().getCode()).isEqualTo("DIREITO");
        assertThat(foundLawStudent.get().getAcademicPeriod()).isEqualTo(7);

        Optional<Student> foundMedStudent = studentRepository.findByPhoneNumber("5534977776666");
        assertThat(foundMedStudent).isPresent();
        assertThat(foundMedStudent.get().getCourse().getCode()).isEqualTo("MEDICINA");
        assertThat(foundMedStudent.get().getAcademicPeriod()).isEqualTo(9);
    }
}
