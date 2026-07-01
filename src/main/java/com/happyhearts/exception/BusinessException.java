package com.happyhearts.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    public BusinessException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }
}
