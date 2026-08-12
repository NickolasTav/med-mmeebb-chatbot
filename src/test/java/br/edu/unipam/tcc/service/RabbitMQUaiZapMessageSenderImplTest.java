package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.UaiZapSendTextRequest;
import br.edu.unipam.tcc.entity.Question;
import br.edu.unipam.tcc.entity.QuestionOption;
import br.edu.unipam.tcc.entity.Specialty;
import br.edu.unipam.tcc.entity.Topic;
import br.edu.unipam.tcc.service.impl.RabbitMQUaiZapMessageSenderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQUaiZapMessageSenderImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitMQUaiZapMessageSenderImpl sender;

    private final String outboundQueue = "q.uaizap.outbound.test";

    @BeforeEach
    void setUp() {
        sender = new RabbitMQUaiZapMessageSenderImpl(rabbitTemplate, outboundQueue);
    }

    @Test
    @DisplayName("Deve enviar mensagem de texto para a fila RabbitMQ correta")
    void shouldSendTextMessageToRabbit() {
        String phone = "5534999999999";
        String message = "Mensagem de teste";

        sender.sendTextMessage(phone, message);

        ArgumentCaptor<UaiZapSendTextRequest> captor = ArgumentCaptor.forClass(UaiZapSendTextRequest.class);
        verify(rabbitTemplate).convertAndSend(eq(outboundQueue), captor.capture());

        UaiZapSendTextRequest captured = captor.getValue();
        assertThat(captured.getNumber()).isEqualTo(phone);
        assertThat(captured.getText()).isEqualTo(message);
    }

    @Test
    @DisplayName("Deve formatar e enviar questão interativa formatada")
    void shouldFormatAndSendInteractiveQuestion() {
        Specialty specialty = Specialty.builder().name("Cardiologia").build();
        Topic topic = Topic.builder().name("Hipertensão Arterial Sistêmica").specialty(specialty).build();
        Question question = Question.builder()
                .topic(topic)
                .statement("Qual a conduta inicial para crise hipertensiva?")
                .options(List.of(
                        QuestionOption.builder().letter('A').optionText("Nitroprussiato de sódio").build(),
                        QuestionOption.builder().letter('B').optionText("Observação clínica").build()
                ))
                .build();

        sender.sendInteractiveQuestion("5534999999999", question);

        ArgumentCaptor<UaiZapSendTextRequest> captor = ArgumentCaptor.forClass(UaiZapSendTextRequest.class);
        verify(rabbitTemplate).convertAndSend(eq(outboundQueue), captor.capture());

        UaiZapSendTextRequest captured = captor.getValue();
        assertThat(captured.getText()).contains("Cardiologia");
        assertThat(captured.getText()).contains("Hipertensão Arterial Sistêmica");
        assertThat(captured.getText()).contains("A)* Nitroprussiato de sódio");
        assertThat(captured.getText()).contains("B)* Observação clínica");
    }
}
