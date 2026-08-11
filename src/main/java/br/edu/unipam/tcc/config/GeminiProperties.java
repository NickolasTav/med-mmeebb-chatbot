package br.edu.unipam.tcc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    /**
     * Chave da API do Google Gemini.
     */
    private String apiKey = "";

    /**
     * Modelo do Gemini a ser utilizado (padrão: gemini-1.5-flash).
     */
    private String model = "gemini-1.5-flash";

    /**
     * URL base da API Generative Language do Google.
     */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * Flag que ativa ou desativa o uso de I.A no chatbot.
     */
    private boolean enabled = true;

    /**
     * Timeout de requisição em segundos.
     */
    private int timeoutSeconds = 10;

    /**
     * Verifica se a API do Gemini está devidamente habilitada e com chave configurada.
     */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("dummy_key");
    }
}
