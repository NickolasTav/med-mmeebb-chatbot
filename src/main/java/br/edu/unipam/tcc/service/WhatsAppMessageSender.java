package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.entity.Question;

public interface WhatsAppMessageSender {

    void sendTextMessage(String phoneNumber, String text);

    void sendInteractiveQuestion(String phoneNumber, Question question);
}
