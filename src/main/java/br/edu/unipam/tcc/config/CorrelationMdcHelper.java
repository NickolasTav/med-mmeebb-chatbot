package br.edu.unipam.tcc.config;

import org.slf4j.MDC;

/**
 * Utilitário para gerenciamento de identificadores de correlação no MDC (Mapped Diagnostic Context).
 * Permite que cada linha de log contenha o telefone do estudante e o ID da mensagem do WhatsApp.
 */
public final class CorrelationMdcHelper {

    public static final String MDC_STUDENT_PHONE = "studentPhone";
    public static final String MDC_MESSAGE_ID = "messageId";
    public static final String MDC_ACTION = "action";

    private CorrelationMdcHelper() {
    }

    public static void setContext(String studentPhone, String messageId, String action) {
        if (studentPhone != null && !studentPhone.isBlank()) {
            MDC.put(MDC_STUDENT_PHONE, studentPhone);
        }
        if (messageId != null && !messageId.isBlank()) {
            MDC.put(MDC_MESSAGE_ID, messageId);
        }
        if (action != null && !action.isBlank()) {
            MDC.put(MDC_ACTION, action);
        }
    }

    public static void clearContext() {
        MDC.remove(MDC_STUDENT_PHONE);
        MDC.remove(MDC_MESSAGE_ID);
        MDC.remove(MDC_ACTION);
    }
}
