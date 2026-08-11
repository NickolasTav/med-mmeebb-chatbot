package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.Specialty;
import br.edu.unipam.tcc.entity.Topic;
import br.edu.unipam.tcc.repository.QuestionRepository;
import br.edu.unipam.tcc.repository.SpecialtyRepository;
import br.edu.unipam.tcc.repository.TopicRepository;
import br.edu.unipam.tcc.service.impl.QuestionImportServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceImplTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private QuestionRepository questionRepository;

    private QuestionImportService service;

    @BeforeEach
    void setUp() {
        service = new QuestionImportServiceImpl(specialtyRepository, topicRepository, questionRepository);
    }

    @Test
    @DisplayName("Deve extrair e cadastrar questões de uma planilha Excel válida")
    void shouldExtractAndSaveQuestionsFromValidExcel() throws IOException {
        byte[] excelBytes = createSampleExcelBytes();

        Specialty specialty = Specialty.builder().id(1L).code("PEDIATRIA").name("Pediatria").build();
        Topic topic = Topic.builder().id(10L).specialty(specialty).name("Desidratação Aguda").build();

        when(specialtyRepository.findByCode("PEDIATRIA")).thenReturn(Optional.of(specialty));
        when(topicRepository.findByNameIgnoreCaseAndSpecialtyId("Desidratação Aguda", 1L)).thenReturn(Optional.of(topic));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Question> savedQuestions = service.importQuestionsFromExcel(new ByteArrayInputStream(excelBytes), "Planilha Dr. Carlos");

        assertEquals(1, savedQuestions.size());
        Question q = savedQuestions.get(0);
        assertEquals("Lactente de 8 meses com diarreia aguda...", q.getStatement());
        assertEquals("Plano B é o tratamento de escolha.", q.getClinicalExplanation());
        assertEquals("MEDIUM", q.getDifficulty());
        assertEquals(4, q.getOptions().size());
        assertTrue(q.getOptions().stream().anyMatch(opt -> opt.getLetter().equals('B') && opt.getIsCorrect()));
    }

    private byte[] createSampleExcelBytes() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Questoes");
            
            // Header
            Row header = sheet.createRow(0);
            String[] headers = {"Especialidade", "Topico", "Tipo", "Enunciado", "Opcao_A", "Opcao_B", "Opcao_C", "Opcao_D", "Opcao_E", "Gabarito", "Justificativa", "Dificuldade"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Data Row
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("PEDIATRIA");
            row.createCell(1).setCellValue("Desidratação Aguda");
            row.createCell(2).setCellValue("MULTIPLE_CHOICE");
            row.createCell(3).setCellValue("Lactente de 8 meses com diarreia aguda...");
            row.createCell(4).setCellValue("Plano A");
            row.createCell(5).setCellValue("Plano B");
            row.createCell(6).setCellValue("Plano C");
            row.createCell(7).setCellValue("Antibiótico");
            row.createCell(8).setCellValue("");
            row.createCell(9).setCellValue("B");
            row.createCell(10).setCellValue("Plano B é o tratamento de escolha.");
            row.createCell(11).setCellValue("MEDIUM");

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
