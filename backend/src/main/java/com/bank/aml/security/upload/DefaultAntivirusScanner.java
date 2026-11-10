package com.bank.aml.security.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DefaultAntivirusScanner implements AntivirusScanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAntivirusScanner.class);

    // EICAR Standard Antivirus Test String
    private static final String EICAR_SIGNATURE = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    // Suspicious active script execution triggers inside policy documents
    private static final List<String> MALICIOUS_SCRIPT_SIGNATURES = List.of(
        "powershell.exe -enc",
        "cmd.exe /c",
        "WScript.Shell",
        "<script language=\"javascript\">",
        "AutoOpen()",
        "vbaProject.bin"
    );

    @Override
    public ScanResult scan(byte[] fileBytes, String filename) {
        if (fileBytes == null || fileBytes.length == 0) {
            return ScanResult.ok();
        }

        // 1. Check for standard EICAR test signature
        String contentSnippet = new String(fileBytes, 0, Math.min(fileBytes.length, 4096), StandardCharsets.ISO_8859_1);
        if (contentSnippet.contains(EICAR_SIGNATURE)) {
            log.warn("ANTIVIRUS ALERT: EICAR malware test signature detected in upload '{}'", filename);
            return ScanResult.infected("EICAR_TEST_VIRUS", "Standard EICAR antivirus test signature identified.");
        }

        // 2. Check for Windows PE Executable magic bytes (MZ = 0x4D, 0x5A)
        if (fileBytes.length >= 2 && fileBytes[0] == 0x4D && fileBytes[1] == 0x5A) {
            log.warn("ANTIVIRUS ALERT: Disguised Windows Executable (PE/MZ) binary detected in '{}'", filename);
            return ScanResult.infected("DISGUISED_PE_BINARY", "File contains Windows executable headers (MZ).");
        }

        // 3. Check for Linux ELF Executable magic bytes (0x7F, 'E', 'L', 'F')
        if (fileBytes.length >= 4 && fileBytes[0] == 0x7F && fileBytes[1] == 'E' && fileBytes[2] == 'L' && fileBytes[3] == 'F') {
            log.warn("ANTIVIRUS ALERT: Disguised Linux ELF binary detected in '{}'", filename);
            return ScanResult.infected("DISGUISED_ELF_BINARY", "File contains Linux ELF executable headers.");
        }

        // 4. Check for embedded active malicious scripting macros
        String fullIsoContent = new String(fileBytes, StandardCharsets.ISO_8859_1);
        for (String sig : MALICIOUS_SCRIPT_SIGNATURES) {
            if (fullIsoContent.contains(sig)) {
                log.warn("ANTIVIRUS ALERT: Malicious active script/macro signature '{}' detected in '{}'", sig, filename);
                return ScanResult.infected("MALICIOUS_MACRO_SCRIPT", "File contains active script or shell execution triggers: " + sig);
            }
        }

        return ScanResult.ok();
    }
}
