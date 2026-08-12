package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.gemini.AiIntentResult;
import br.edu.unipam.tcc.entity.InteractionLog;
import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.Student;

import java.util.List;

public interface GeminiAiService {

    /**
     * Analisa uma mensagem em linguagem natural livre do estudante com o contexto enriquecido
     * do aluno (curso, período, questão ativa ou histórico recente).
     *
     * @param student Estudante remetente.
     * @param incomingMessage Mensagem enviada pelo aluno no WhatsApp.
     * @param activeQuestion Questão pendente aguardando resposta (se houver).
     * @param lastLog Última interação recente do aluno (se houver).
     * @param availableAreas Áreas de conhecimento/disciplinas disponíveis.
     * @return Resultado estruturado com intenção, opção extraída e/ou resposta pedagógica contextualizada.
     */
    AiIntentResult analyzeMessage(
            Student student,
            String incomingMessage,
            Question activeQuestion,
            InteractionLog lastLog,
            List<String> availableAreas
    );

    /**
     * Método sobrecarregado de análise simplificada para compatibilidade.
     */
    AiIntentResult analyzeMessage(String studentName, String incomingMessage, List<String> availableSpecialties);

    /**
     * Atua no modo Tutor Pedagógico Acadêmico Especialista: gera uma explicação personalizada
     * adaptada ao tom e à persona do curso do aluno (Medicina, Direito, TI, etc.).
     *
     * @param student Estudante remetente com seu curso e persona.
     * @param question Questão com enunciado e justificativa comentada.
     * @param studentDoubt Dúvida ou questionamento feito pelo aluno.
     * @return Explicação didática formatada para o WhatsApp.
     */
    String generateAcademicTutorExplanation(Student student, Question question, String studentDoubt);

    /**
     * Método de compatibilidade legado para tutor clínico.
     */
    String generateClinicalTutorExplanation(String studentName, String statement, String clinicalExplanation, String studentDoubt);

    /**
     * Verifica se o serviço de IA do Gemini está ativo e operacional.
     */
    boolean isAvailable();
}
