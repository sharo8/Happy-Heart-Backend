package com.happyhearts.exception;

public class RfidCardNotFoundException extends BusinessException {

    public RfidCardNotFoundException() {
        super("error.rfid.card.not.found");
    }
}
