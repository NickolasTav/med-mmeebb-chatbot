package br.edu.unipam.tcc.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UaiZapWebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deve desserializar corretamente o JSON real do webhook da Uazapi (EventType, message, chat)")
    void shouldDeserializeUazapiRealPayload() throws Exception {
        String json = """
        {
            "BaseUrl": "https://free.uazapi.com",
            "EventType": "messages",
            "chat": {
                "chatbot_agentResetMemoryAt": 0,
                "chatbot_disableUntil": 0,
                "chatbot_lastTriggerAt": 0,
                "chatbot_lastTrigger_id": "",
                "id": "r63c929527413c4",
                "image": "",
                "imagePreview": "",
                "lead_assignedAttendant_id": "",
                "name": "Rafael Faculdade",
                "owner": "553499754953",
                "phone": "+55 34 9821-7808",
                "wa_archived": false,
                "wa_chatid": "553498217808@s.whatsapp.net",
                "wa_chatlid": "212288019648554@lid",
                "wa_contactName": "Rafael Faculdade",
                "wa_isBlocked": false,
                "wa_isGroup": false,
                "wa_name": "Rafael"
            },
            "chatSource": "updated",
            "instanceName": "teste",
            "message": {
                "chatid": "553498217808@s.whatsapp.net",
                "chatlid": "212288019648554@lid",
                "content": "olá?",
                "fromMe": false,
                "id": "553499754953:ACD9E19035A6AA4327A704AE66A81B45",
                "isGroup": false,
                "messageTimestamp": 1786470633000,
                "messageType": "Conversation",
                "messageid": "ACD9E19035A6AA4327A704AE66A81B45",
                "owner": "553499754953",
                "sender": "212288019648554@lid",
                "senderName": "Rafael",
                "sender_lid": "212288019648554@lid",
                "sender_pn": "553498217808@s.whatsapp.net",
                "source": "android",
                "text": "olá?",
                "type": "text",
                "wasSentByApi": false
            },
            "owner": "553499754953",
            "token": "79db7830-d461-4dd1-8499-a0bd72a6702d"
        }
        """;

        UaiZapWebhookPayload payload = objectMapper.readValue(json, UaiZapWebhookPayload.class);

        assertThat(payload).isNotNull();
        assertThat(payload.getEffectiveEvent()).isEqualTo("messages");
        assertThat(payload.extractSenderPhone()).isEqualTo("553498217808");
        assertThat(payload.extractMessageText()).isEqualTo("olá?");
        assertThat(payload.extractSenderName()).isEqualTo("Rafael");
        assertThat(payload.extractMessageId()).isEqualTo("ACD9E19035A6AA4327A704AE66A81B45");
        assertThat(payload.isFromMe()).isFalse();
        assertThat(payload.isGroup()).isFalse();
    }
}
