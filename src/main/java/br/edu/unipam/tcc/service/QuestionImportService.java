package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.entity.Question;

import java.io.InputStream;
import java.util.List;

public interface QuestionImportService {

    List<Question> importQuestionsFromExcel(InputStream inputStream, String sourceTag);
}
