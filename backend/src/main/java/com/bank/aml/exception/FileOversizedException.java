package com.bank.aml.exception;

public class FileOversizedException extends InvalidFileException {
    public FileOversizedException(String message) {
        super(message);
    }
}
