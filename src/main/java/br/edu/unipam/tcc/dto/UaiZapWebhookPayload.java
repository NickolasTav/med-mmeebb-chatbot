package br.edu.unipam.tcc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UaiZapWebhookPayload implements Serializable {

    private String event;
    private String instance;
    private MessageData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageData implements Serializable {
        private MessageKey key;
        private String pushName;
        private String messageType;
        private MessageContent message;
        private Long messageTimestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey implements Serializable {
        private String remoteJid;
        private Boolean fromMe;
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageContent implements Serializable {
        private String conversation;

        @JsonProperty("extendedTextMessage")
        private ExtendedText extendedTextMessage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedText implements Serializable {
        private String text;
    }

    public String extractSenderPhone() {
        if (data != null && data.getKey() != null && data.getKey().getRemoteJid() != null) {
            return data.getKey().getRemoteJid().replaceAll("@.*", "").replaceAll("[^0-9]", "");
        }
        return null;
    }

    public String extractMessageText() {
        if (data != null && data.getMessage() != null) {
            if (data.getMessage().getConversation() != null) {
                return data.getMessage().getConversation().trim();
            }
            if (data.getMessage().getExtendedTextMessage() != null && data.getMessage().getExtendedTextMessage().getText() != null) {
                return data.getMessage().getExtendedTextMessage().getText().trim();
            }
        }
        return "";
    }
}
