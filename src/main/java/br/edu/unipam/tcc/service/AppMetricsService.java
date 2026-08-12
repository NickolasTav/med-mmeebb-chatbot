package br.edu.unipam.tcc.service;

/**
 * Contrato de serviço para registro de métricas de negócio do Chatbot MMEEBB.
 */
public interface AppMetricsService {

    /**
     * Registra a resposta de uma questão de revisão espaçada pelo aluno.
     *
     * @param isCorrect true se o aluno acertou, false se errou
     * @param specialty especialidade médica da questão
     */
    void recordReviewAnswer(boolean isCorrect, String specialty);

    /**
     * Registra uma interação com a IA (Google Gemini ou Fast-Path).
     *
     * @param interactionType tipo da interação (ex: INTENT_CLASSIFICATION, CLINICAL_TUTOR)
     * @param isFallback      indica se houve fallback por ausência de chave ou erro
     */
    void recordAiInteraction(String interactionType, boolean isFallback);

    /**
     * Registra o tráfego de mensagens do WhatsApp (UaiZap).
     *
     * @param direction   INBOUND (recebida) ou OUTBOUND (enviada)
     * @param messageType tipo da mensagem (ex: TEXT, QUESTION, REMINDER)
     */
    void recordWhatsAppMessage(String direction, String messageType);
}
