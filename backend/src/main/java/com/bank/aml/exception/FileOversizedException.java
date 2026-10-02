package com.bank.aml.exception;

public class FileOversizedException extends RuntimeException {
    public FileOversizedException(String message) {
        super(message);
    }
}
