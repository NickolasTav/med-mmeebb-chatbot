package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.gemini.AiIntentResult;

import java.util.List;

public interface GeminiAiService {

    /**
     * Analisa uma mensagem em linguagem natural livre do estudante, classificando a intenção
     * e identificando se há especialidade médica requisitada ou pergunta aberta.
     *
     * @param studentName Nome do estudante.
     * @param incomingMessage Mensagem enviada pelo aluno no WhatsApp.
     * @param availableSpecialties Lista de códigos/nomes de especialidades médicas disponíveis no sistema.
     * @return Resultado estruturado com intenção, especialidade e/ou mensagem de resposta gerada.
     */
    AiIntentResult analyzeMessage(String studentName, String incomingMessage, List<String> availableSpecialties);

    /**
     * Atua no modo Tutor Clínico: gera uma explicação pedagógica personalizada para responder
     * à dúvida do estudante sobre uma questão médica previamente respondida.
     *
     * @param studentName Nome do estudante.
     * @param statement Enunciado do caso clínico.
     * @param clinicalExplanation Justificativa comentada do gabarito oficial.
     * @param studentDoubt Dúvida ou questionamento feito pelo aluno.
     * @return Explicação clínica didática e concisa.
     */
    String generateClinicalTutorExplanation(String studentName, String statement, String clinicalExplanation, String studentDoubt);

    /**
     * Verifica se o serviço de IA do Gemini está ativo e operacional.
     */
    boolean isAvailable();
}
