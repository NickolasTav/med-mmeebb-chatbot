package br.edu.unipam.tcc.controller;

import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.service.QuestionImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/questions/import")
@RequiredArgsConstructor
public class QuestionImportController {

    private final QuestionImportService questionImportService;

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false, defaultValue = "Docente UNIPAM") String source
    ) throws Exception {
        if (file.isEmpty()) {
            throw new br.edu.unipam.tcc.exception.BusinessException("error.empty_file");
        }

        List<Question> imported = questionImportService.importQuestionsFromExcel(file.getInputStream(), source);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "totalImported", imported.size(),
                "source", source
        ));
    }
}
