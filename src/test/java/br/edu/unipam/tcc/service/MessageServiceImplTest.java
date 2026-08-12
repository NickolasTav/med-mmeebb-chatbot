package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.config.I18nConfig;
import br.edu.unipam.tcc.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceImplTest {

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(I18nConfig.DEFAULT_LOCALE);
        messageSource.setFallbackToSystemLocale(false);

        messageService = new MessageServiceImpl(messageSource);
    }

    @Test
    @DisplayName("Deve buscar mensagem em Português (padrão) com interpolação de parâmetros")
    void shouldGetPortugueseMessage() {
        String msg = messageService.getMessage("bot.welcome", "Lucas", "Medicina");
        assertThat(msg).contains("Bem-vindo(a) ao *Bot MMEEBB de Repetição Espaçada (Medicina - UNIPAM)*");

        String menu = messageService.getMessage("bot.menu", "Lucas");
        assertThat(menu).contains("Olá, *Lucas*!");
    }

    @Test
    @DisplayName("Deve buscar mensagem em Inglês quando Locale.ENGLISH for especificado")
    void shouldGetEnglishMessage() {
        String msg = messageService.getMessage("bot.welcome", Locale.ENGLISH, "Lucas", "Medicine");
        assertThat(msg).contains("Welcome to the *MMEEBB Spaced Repetition Bot (Medicine - UNIPAM)*");

        String menu = messageService.getMessage("bot.menu", Locale.ENGLISH, "Lucas");
        assertThat(menu).contains("Hello, *Lucas*!");
    }

    @Test
    @DisplayName("Deve buscar mensagem em Espanhol quando Locale('es') for especificado")
    void shouldGetSpanishMessage() {
        String msg = messageService.getMessage("bot.welcome", Locale.forLanguageTag("es"), "Lucas", "Medicina");
        assertThat(msg).contains("Bienvenido(a) al *Bot MMEEBB de Repetición Espaciada (Medicina - UNIPAM)*");

        String menu = messageService.getMessage("bot.menu", Locale.forLanguageTag("es"), "Lucas");
        assertThat(menu).contains("¡Hola, *Lucas*!");
    }

    @Test
    @DisplayName("Deve retornar a própria chave caso a mensagem não exista no bundle")
    void shouldReturnCodeWhenKeyNotFound() {
        String result = messageService.getMessage("chave.inexistente.teste");
        assertThat(result).isEqualTo("chave.inexistente.teste");
    }
}
