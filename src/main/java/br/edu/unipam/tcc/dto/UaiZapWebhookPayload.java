package br.edu.unipam.tcc.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias({"EventType", "event_type"})
    private String event;

    @JsonAlias({"instanceName", "instance_name"})
    private String instance;

    @JsonAlias({"BaseUrl", "base_url"})
    private String baseUrl;

    private String owner;
    private String token;
    private String chatSource;

    // Estrutura nativa Uazapi (uazapiGO / free.uazapi.com)
    private UazapiMessage message;
    private UazapiChat chat;

    // Estrutura Evolution API / Baileys legado
    private MessageData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UazapiMessage implements Serializable {
        private String id;
        private String messageid;
        private String chatid;
        private String chatlid;
        private String sender;
        private String senderName;
        private String sender_pn;
        private String sender_lid;
        private String text;
        private String content;
        private String type;
        private String messageType;
        private Long messageTimestamp;
        private Boolean fromMe;
        private Boolean wasSentByApi;
        private Boolean isGroup;
        private String owner;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UazapiChat implements Serializable {
        private String id;
        private String name;
        private String phone;
        private String wa_chatid;
        private String wa_chatlid;
        private String wa_name;
        private String wa_contactName;
        private Boolean wa_isGroup;
    }

    @Data
    @Builder
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey implements Serializable {
        private String remoteJid;
        private Boolean fromMe;
        private String id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageContent implements Serializable {
        private String conversation;

        @JsonProperty("extendedTextMessage")
        private ExtendedText extendedTextMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedText implements Serializable {
        private String text;
    }

    public String getEffectiveEvent() {
        if (event != null && !event.isBlank()) {
            return event;
        }
        return "UNKNOWN";
    }

    public boolean isFromMe() {
        if (message != null) {
            if (Boolean.TRUE.equals(message.getFromMe()) || Boolean.TRUE.equals(message.getWasSentByApi())) {
                return true;
            }
        }
        if (data != null && data.getKey() != null && Boolean.TRUE.equals(data.getKey().getFromMe())) {
            return true;
        }
        return false;
    }

    public boolean isGroup() {
        if (message != null && Boolean.TRUE.equals(message.getIsGroup())) {
            return true;
        }
        if (chat != null && Boolean.TRUE.equals(chat.getWa_isGroup())) {
            return true;
        }
        if (data != null && data.getKey() != null && data.getKey().getRemoteJid() != null) {
            return data.getKey().getRemoteJid().contains("@g.us");
        }
        return false;
    }

    public String extractSenderPhone() {
        // 1. Tenta pegar do sender_pn da Uazapi (ex: 553498217808@s.whatsapp.net)
        if (message != null && message.getSender_pn() != null && !message.getSender_pn().isBlank()) {
            return cleanPhoneNumber(message.getSender_pn());
        }
        // 2. Tenta pegar do chatid da Uazapi se não for grupo nem lid
        if (message != null && message.getChatid() != null && !message.getChatid().contains("@g.us") && !message.getChatid().contains("@lid")) {
            return cleanPhoneNumber(message.getChatid());
        }
        // 3. Tenta pegar do wa_chatid do Chat
        if (chat != null && chat.getWa_chatid() != null && !chat.getWa_chatid().contains("@g.us") && !chat.getWa_chatid().contains("@lid")) {
            return cleanPhoneNumber(chat.getWa_chatid());
        }
        // 4. Tenta pegar do phone do Chat (ex: +55 34 9821-7808)
        if (chat != null && chat.getPhone() != null && !chat.getPhone().isBlank()) {
            return cleanPhoneNumber(chat.getPhone());
        }
        // 5. Fallback para formato Evolution/Baileys
        if (data != null && data.getKey() != null && data.getKey().getRemoteJid() != null) {
            return cleanPhoneNumber(data.getKey().getRemoteJid());
        }
        return null;
    }

    public String extractMessageText() {
        // 1. Uazapi: message.text ou message.content
        if (message != null) {
            if (message.getText() != null && !message.getText().isBlank()) {
                return message.getText().trim();
            }
            if (message.getContent() != null && !message.getContent().isBlank()) {
                return message.getContent().trim();
            }
        }
        // 2. Evolution: data.message
        if (data != null && data.getMessage() != null) {
            if (data.getMessage().getConversation() != null && !data.getMessage().getConversation().isBlank()) {
                return data.getMessage().getConversation().trim();
            }
            if (data.getMessage().getExtendedTextMessage() != null && data.getMessage().getExtendedTextMessage().getText() != null) {
                return data.getMessage().getExtendedTextMessage().getText().trim();
            }
        }
        return "";
    }

    public String extractSenderName() {
        if (message != null && message.getSenderName() != null && !message.getSenderName().isBlank()) {
            return message.getSenderName().trim();
        }
        if (chat != null) {
            if (chat.getWa_name() != null && !chat.getWa_name().isBlank()) {
                return chat.getWa_name().trim();
            }
            if (chat.getWa_contactName() != null && !chat.getWa_contactName().isBlank()) {
                return chat.getWa_contactName().trim();
            }
            if (chat.getName() != null && !chat.getName().isBlank()) {
                return chat.getName().trim();
            }
        }
        if (data != null && data.getPushName() != null && !data.getPushName().isBlank()) {
            return data.getPushName().trim();
        }
        return "Estudante";
    }

    public String extractMessageId() {
        if (message != null) {
            if (message.getMessageid() != null && !message.getMessageid().isBlank()) {
                return message.getMessageid();
            }
            if (message.getId() != null && !message.getId().isBlank()) {
                return message.getId();
            }
        }
        if (data != null && data.getKey() != null && data.getKey().getId() != null) {
            return data.getKey().getId();
        }
        return null;
    }

    private static String cleanPhoneNumber(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("@.*", "").replaceAll("[^0-9]", "");
        return cleaned.isBlank() ? null : cleaned;
    }
}
