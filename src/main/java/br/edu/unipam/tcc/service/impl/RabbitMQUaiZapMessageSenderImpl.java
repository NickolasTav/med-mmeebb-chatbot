package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.dto.UaiZapSendTextRequest;
import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.QuestionOption;
import br.edu.unipam.tcc.service.WhatsAppMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Slf4j
@Service
public class RabbitMQUaiZapMessageSenderImpl implements WhatsAppMessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final String directExchange;
    private final String outboundRoutingKey;

    public RabbitMQUaiZapMessageSenderImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchanges.med-direct:ex.med.direct}") String directExchange,
            @Value("${rabbitmq.routing-keys.outbound:rk.uaizap.outbound}") String outboundRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.directExchange = directExchange;
        this.outboundRoutingKey = outboundRoutingKey;
    }

    @Override
    public void sendTextMessage(String phoneNumber, String text) {
        if (phoneNumber == null || text == null || text.isBlank()) {
            return;
        }

        UaiZapSendTextRequest request = UaiZapSendTextRequest.builder()
                .number(phoneNumber)
                .text(text)
                .build();

        rabbitTemplate.convertAndSend(directExchange, outboundRoutingKey, request);
        log.info("Mensagem de texto enviada para a fila RabbitMQ [{}] -> {}", outboundRoutingKey, phoneNumber);
    }

    @Override
    public void sendInteractiveQuestion(String phoneNumber, Question question) {
        if (phoneNumber == null || question == null) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("🩺 *REVISÃO CLÍNICA DO INTERNATO MÉDICO (MMEEBB)*\n");
        if (question.getTopic() != null) {
            if (question.getTopic().getSpecialty() != null) {
                msg.append("📚 *Especialidade:* ").append(question.getTopic().getSpecialty().getName()).append("\n");
            }
            msg.append("📌 *Tema:* ").append(question.getTopic().getName()).append("\n\n");
        }

        msg.append("📝 *Caso Clínico / Enunciado:*\n");
        msg.append(question.getStatement()).append("\n\n");

        msg.append("👉 *Alternativas:*\n");
        if (question.getOptions() != null) {
            question.getOptions().stream()
                    .sorted(Comparator.comparing(QuestionOption::getLetter))
                    .forEach(opt -> msg.append("*").append(opt.getLetter()).append(")* ")
                            .append(opt.getOptionText()).append("\n"));
        }

        msg.append("\n💬 _Responda diretamente com a letra correspondente (ex: *A*, *B*, *C* ou *D*)._");

        sendTextMessage(phoneNumber, msg.toString());
    }
}
