package br.edu.unipam.tcc.service;

import java.util.Locale;

public interface MessageService {

    String getMessage(String code, Object... args);

    String getMessage(String code, Locale locale, Object... args);

    Locale getCurrentLocale();
}
