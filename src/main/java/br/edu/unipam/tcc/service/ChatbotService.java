package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.UaiZapWebhookPayload;

public interface ChatbotService {

    void processIncomingMessage(UaiZapWebhookPayload payload);

    void sendWhatsAppMessage(String phoneNumber, String text);
}
