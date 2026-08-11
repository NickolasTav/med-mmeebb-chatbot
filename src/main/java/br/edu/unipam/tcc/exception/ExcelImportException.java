package br.edu.unipam.tcc.exception;

public class ExcelImportException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public ExcelImportException(String message) {
        super(message);
        this.messageKey = null;
        this.args = new Object[0];
    }

    public ExcelImportException(String message, Throwable cause) {
        super(message, cause);
        this.messageKey = null;
        this.args = new Object[0];
    }

    public ExcelImportException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
