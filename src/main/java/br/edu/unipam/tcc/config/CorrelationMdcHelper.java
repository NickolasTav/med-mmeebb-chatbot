package br.edu.unipam.tcc.config;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utilitário para gerenciamento de identificadores de correlação no MDC (Mapped Diagnostic Context).
 * Garante que cada linha de log contenha o traceId, telefone do estudante e ID da mensagem do WhatsApp,
 * mesmo em fluxos assíncronos ou rotinas agendadas onde o Tracer ainda não inicializou um span.
 */
public final class CorrelationMdcHelper {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_STUDENT_PHONE = "studentPhone";
    public static final String MDC_MESSAGE_ID = "messageId";
    public static final String MDC_ACTION = "action";

    private CorrelationMdcHelper() {
    }

    /**
     * Configura o contexto do MDC. Se o traceId não estiver presente na thread atual,
     * gera automaticamente um identificador hexadecimal de 16 caracteres compatível com W3C/B3.
     */
    public static void setContext(String studentPhone, String messageId, String action) {
        setContext(studentPhone, messageId, action, null);
    }

    /**
     * Configura o contexto do MDC permitindo fornecer um traceId explícito.
     */
    public static void setContext(String studentPhone, String messageId, String action, String explicitTraceId) {
        if (explicitTraceId != null && !explicitTraceId.isBlank()) {
            MDC.put(MDC_TRACE_ID, explicitTraceId);
        } else {
            ensureTraceId();
        }

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

    /**
     * Garante que a thread atual possua um traceId registrado no MDC.
     * Retorna o traceId existente ou o recém-gerado.
     */
    public static String ensureTraceId() {
        String existingTraceId = MDC.get(MDC_TRACE_ID);
        if (existingTraceId != null && !existingTraceId.isBlank()) {
            return existingTraceId;
        }

        String generatedTraceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(MDC_TRACE_ID, generatedTraceId);
        return generatedTraceId;
    }

    /**
     * Obtém o traceId atual do MDC, se houver.
     */
    public static String getTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    /**
     * Limpa todos os identificadores de correlação injetados no MDC da thread atual.
     */
    public static void clearContext() {
        MDC.remove(MDC_STUDENT_PHONE);
        MDC.remove(MDC_MESSAGE_ID);
        MDC.remove(MDC_ACTION);
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }
}

