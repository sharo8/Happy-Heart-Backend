package com.happyhearts.exception;

public class BranchNotFoundException extends BusinessException {

    public BranchNotFoundException() {
        super("error.branch.not.found");
    }
}
