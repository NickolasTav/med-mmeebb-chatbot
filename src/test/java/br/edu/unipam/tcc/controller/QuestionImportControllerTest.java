package br.edu.unipam.tcc.controller;

import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.exception.BusinessException;
import br.edu.unipam.tcc.service.QuestionImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportControllerTest {

    @Mock
    private QuestionImportService questionImportService;

    private QuestionImportController controller;

    @BeforeEach
    void setUp() {
        controller = new QuestionImportController(questionImportService);
    }

    @Test
    @DisplayName("Deve realizar upload e importar questões via QuestionImportService")
    void shouldImportQuestionsViaController() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questoes.xlsx", "application/vnd.ms-excel", new byte[]{1, 2, 3});
        when(questionImportService.importQuestionsFromExcel(any(InputStream.class), eq("Docente Pediatria")))
                .thenReturn(List.of(Question.builder().id(1L).build(), Question.builder().id(2L).build()));

        ResponseEntity<Map<String, Object>> response = controller.importExcel(file, "Docente Pediatria");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "SUCCESS");
        assertThat(response.getBody()).containsEntry("totalImported", 2);
        verify(questionImportService).importQuestionsFromExcel(any(InputStream.class), eq("Docente Pediatria"));
    }

    @Test
    @DisplayName("Deve lançar BusinessException para arquivo vazio")
    void shouldThrowBusinessExceptionForEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "vazio.xlsx", "application/vnd.ms-excel", new byte[0]);

        assertThatThrownBy(() -> controller.importExcel(emptyFile, "Docente"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.empty_file");
    }
}
