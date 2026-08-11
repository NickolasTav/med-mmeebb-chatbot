package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.QuestionOption;
import br.edu.unipam.tcc.entity.Specialty;
import br.edu.unipam.tcc.entity.Topic;
import br.edu.unipam.tcc.exception.ExcelImportException;
import br.edu.unipam.tcc.repository.QuestionRepository;
import br.edu.unipam.tcc.repository.SpecialtyRepository;
import br.edu.unipam.tcc.repository.TopicRepository;
import br.edu.unipam.tcc.service.QuestionImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl implements QuestionImportService {

    private final SpecialtyRepository specialtyRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    @Override
    @Transactional
    public List<Question> importQuestionsFromExcel(InputStream inputStream, String sourceTag) {
        List<Question> importedQuestions = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                String specialtyCode = getCellValue(row.getCell(0)).toUpperCase();
                String topicName = getCellValue(row.getCell(1));
                String questionType = getCellValue(row.getCell(2));
                if (questionType.isBlank()) questionType = "MULTIPLE_CHOICE";
                String statement = getCellValue(row.getCell(3));
                String optA = getCellValue(row.getCell(4));
                String optB = getCellValue(row.getCell(5));
                String optC = getCellValue(row.getCell(6));
                String optD = getCellValue(row.getCell(7));
                String optE = getCellValue(row.getCell(8));
                String correctLetter = getCellValue(row.getCell(9)).toUpperCase();
                String explanation = getCellValue(row.getCell(10));
                String difficulty = getCellValue(row.getCell(11)).toUpperCase();
                if (difficulty.isBlank()) difficulty = "MEDIUM";

                Specialty specialty = specialtyRepository.findByCode(specialtyCode)
                        .orElseGet(() -> specialtyRepository.save(
                                Specialty.builder()
                                        .code(specialtyCode)
                                        .name(specialtyCode)
                                        .description("Importado via Planilha")
                                        .build()
                        ));

                Topic topic = topicRepository.findByNameIgnoreCaseAndSpecialtyId(topicName, specialty.getId())
                        .orElseGet(() -> topicRepository.save(
                                Topic.builder()
                                        .specialty(specialty)
                                        .name(topicName)
                                        .summaryText("Tópico importado de questão")
                                        .build()
                        ));

                Question question = Question.builder()
                        .topic(topic)
                        .questionType(questionType)
                        .statement(statement)
                        .clinicalExplanation(explanation)
                        .difficulty(difficulty)
                        .source(sourceTag != null ? sourceTag : "Excel Import")
                        .active(true)
                        .options(new ArrayList<>())
                        .build();

                addOptionIfPresent(question, 'A', optA, correctLetter);
                addOptionIfPresent(question, 'B', optB, correctLetter);
                addOptionIfPresent(question, 'C', optC, correctLetter);
                addOptionIfPresent(question, 'D', optD, correctLetter);
                addOptionIfPresent(question, 'E', optE, correctLetter);

                Question saved = questionRepository.save(question);
                importedQuestions.add(saved);
            }

        } catch (Exception e) {
            log.error("Erro ao importar questões do Excel: {}", e.getMessage(), e);
            throw new ExcelImportException("error.excel_import", e.getMessage());
        }

        return importedQuestions;
    }

    private void addOptionIfPresent(Question question, Character letter, String text, String correctLetter) {
        if (text != null && !text.trim().isEmpty()) {
            boolean isCorrect = correctLetter != null && correctLetter.equalsIgnoreCase(String.valueOf(letter));
            QuestionOption option = QuestionOption.builder()
                    .question(question)
                    .letter(letter)
                    .optionText(text.trim())
                    .isCorrect(isCorrect)
                    .build();
            question.getOptions().add(option);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValue(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
