package br.edu.unipam.tcc.dto.gemini;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIntentResult {

    public enum IntentType {
        ANSWER_ACTIVE_QUESTION,
        REQUEST_REVIEW,
        EXPLAIN_CONCEPT,
        STATS,
        HELP,
        GENERAL_CHAT,
        UNKNOWN
    }

    private IntentType intent;
    private Character extractedOption;
    private String targetKnowledgeArea;
    private String responseMessage;
    private boolean handledByAi;

    public static AiIntentResult fallback(String message) {
        return AiIntentResult.builder()
                .intent(IntentType.UNKNOWN)
                .responseMessage(message)
                .handledByAi(false)
                .build();
    }
}
