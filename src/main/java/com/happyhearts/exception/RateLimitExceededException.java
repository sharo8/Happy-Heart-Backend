package com.happyhearts.exception;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super("error.rate.limit");
    }
}
