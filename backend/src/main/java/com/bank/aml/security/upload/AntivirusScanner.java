package com.bank.aml.security.upload;

public interface AntivirusScanner {

    record ScanResult(
        boolean clean,
        String threatName,
        String details
    ) {
        public static ScanResult ok() {
            return new ScanResult(true, null, "File passed antivirus and integrity checks.");
        }

        public static ScanResult infected(String threatName, String details) {
            return new ScanResult(false, threatName, details);
        }
    }

    ScanResult scan(byte[] fileBytes, String filename);
}
