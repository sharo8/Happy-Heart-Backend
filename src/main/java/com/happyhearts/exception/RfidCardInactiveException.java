package com.happyhearts.exception;

public class RfidCardInactiveException extends BusinessException {

    public RfidCardInactiveException() {
        super("error.rfid.card.inactive");
    }
}
