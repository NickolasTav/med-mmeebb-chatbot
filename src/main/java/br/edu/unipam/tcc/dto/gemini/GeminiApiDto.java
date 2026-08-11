package br.edu.unipam.tcc.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

public class GeminiApiDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiRequest {
        private List<Content> contents;
        private GenerationConfig generationConfig;

        public static GeminiRequest ofSingleText(String text) {
            return GeminiRequest.builder()
                    .contents(List.of(Content.builder()
                            .parts(List.of(new Part(text)))
                            .build()))
                    .generationConfig(GenerationConfig.builder()
                            .temperature(0.2)
                            .maxOutputTokens(800)
                            .build())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationConfig {
        private Double temperature;
        private Integer maxOutputTokens;
        private String responseMimeType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiResponse {
        private List<Candidate> candidates;

        public String extractFirstText() {
            if (candidates == null || candidates.isEmpty()) {
                return "";
            }
            Candidate firstCandidate = candidates.get(0);
            if (firstCandidate == null || firstCandidate.getContent() == null) {
                return "";
            }
            List<Part> parts = firstCandidate.getContent().getParts();
            if (parts == null || parts.isEmpty()) {
                return "";
            }
            return parts.get(0).getText() != null ? parts.get(0).getText().trim() : "";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;
        private String finishReason;
    }
}
