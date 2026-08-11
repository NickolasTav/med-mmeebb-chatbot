package br.edu.unipam.tcc.exception;

public class BusinessException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public BusinessException(String message) {
        super(message);
        this.messageKey = null;
        this.args = new Object[0];
    }

    public BusinessException(String messageKey, Object... args) {
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
