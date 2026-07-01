package com.happyhearts.exception;

public class AccessDeniedException extends BusinessException {

    public AccessDeniedException() {
        super("error.access.denied");
    }
}
