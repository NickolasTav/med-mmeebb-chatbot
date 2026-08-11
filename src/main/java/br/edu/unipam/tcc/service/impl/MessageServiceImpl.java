package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.config.I18nConfig;
import br.edu.unipam.tcc.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String code, Object... args) {
        Locale currentLocale = getCurrentLocale();
        return getMessage(code, currentLocale, args);
    }

    @Override
    public String getMessage(String code, Locale locale, Object... args) {
        if (code == null) {
            return "";
        }
        if (locale == null) {
            locale = I18nConfig.DEFAULT_LOCALE;
        }

        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            log.warn("Chave i18n não encontrada: '{}' para locale '{}'", code, locale);
            return code;
        }
    }

    @Override
    public Locale getCurrentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null ? locale : I18nConfig.DEFAULT_LOCALE;
    }
}
