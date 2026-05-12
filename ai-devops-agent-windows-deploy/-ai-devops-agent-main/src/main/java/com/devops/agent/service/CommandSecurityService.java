package com.devops.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Command security validator.
 * Implements whitelist/blacklist mechanism for SSH command execution.
 */
@Slf4j
@Service
public class CommandSecurityService {

    @Value("${security.command.mode:permissive}")
    private String securityMode; // strict, permissive, disabled

    @Value("${security.command.blacklist:}")
    private String blacklistConfig;

    @Value("${security.command.whitelist:}")
    private String whitelistConfig;

    // Dangerous commands that should never be allowed
    private static final List<String> DANGEROUS_COMMANDS = Arrays.asList(
            "rm -rf /",
            "rm -rf /*",
            "mkfs",
            "dd if=/dev/zero",
            "dd if=/dev/urandom",
            ":(){ :|:& };:",  // Fork bomb
            "chmod -R 777 /",
            "chown -R",
            "> /dev/sda",
            "mv /* /dev/null"
    );

    // Patterns for dangerous operations
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
            Pattern.compile("rm\\s+-[^ ]*r[^ ]*f[^ ]*\\s+/(?!home|tmp|var|opt).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=/dev/(zero|urandom).*of=/dev/.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shutdown\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reboot\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("init\\s+[06]"),
            Pattern.compile("systemctl\\s+(stop|disable|mask)\\s+.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("iptables\\s+-F", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ufw\\s+disable", Pattern.CASE_INSENSITIVE),
            Pattern.compile("passwd\\s+root", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Validate a command before execution.
     * 
     * @param command the command to validate
     * @return ValidationResult with allowed status and optional warning
     */
    public ValidationResult validateCommand(String command) {
        if ("disabled".equals(securityMode)) {
            return ValidationResult.allowed();
        }

        if (command == null || command.trim().isEmpty()) {
            return ValidationResult.denied("Empty command");
        }

        String normalizedCommand = command.trim().toLowerCase();

        // Check against dangerous commands list
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (normalizedCommand.contains(dangerous.toLowerCase())) {
                log.warn("Blocked dangerous command: {}", command);
                return ValidationResult.denied("Command blocked: dangerous operation detected");
            }
        }

        // Check against dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                log.warn("Blocked command matching dangerous pattern: {}", command);
                return ValidationResult.denied("Command blocked: matches dangerous pattern");
            }
        }

        // Check blacklist
        if (blacklistConfig != null && !blacklistConfig.isEmpty()) {
            List<String> blacklist = Arrays.asList(blacklistConfig.split(","));
            for (String blocked : blacklist) {
                if (normalizedCommand.contains(blocked.trim().toLowerCase())) {
                    log.warn("Blocked blacklisted command: {}", command);
                    return ValidationResult.denied("Command is in blacklist: " + blocked);
                }
            }
        }

        // In strict mode, check whitelist
        if ("strict".equals(securityMode) && whitelistConfig != null && !whitelistConfig.isEmpty()) {
            List<String> whitelist = Arrays.asList(whitelistConfig.split(","));
            boolean allowed = false;
            for (String allowedCmd : whitelist) {
                if (normalizedCommand.startsWith(allowedCmd.trim().toLowerCase())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                log.warn("Command not in whitelist (strict mode): {}", command);
                return ValidationResult.denied("Command not in whitelist (strict mode)");
            }
        }

        // Check for potential warnings
        String warning = checkForWarnings(command);
        if (warning != null) {
            log.info("Command allowed with warning: {} - {}", command, warning);
            return ValidationResult.allowedWithWarning(warning);
        }

        return ValidationResult.allowed();
    }

    /**
     * Check for commands that should trigger warnings but are still allowed.
     */
    private String checkForWarnings(String command) {
        String lower = command.toLowerCase();

        if (lower.contains("rm ") && !lower.contains("-i")) {
            return "Deleting files without confirmation (-i flag recommended)";
        }
        if (lower.contains("chmod ") && lower.contains("777")) {
            return "Setting world-writable permissions (777) is a security risk";
        }
        if (lower.contains("curl ") || lower.contains("wget ")) {
            if (lower.contains("| ") || lower.contains("> ")) {
                return "Downloading and executing remote content - verify source";
            }
        }
        if (lower.contains("docker ") && (lower.contains("rm ") || lower.contains("rmi "))) {
            return "Removing Docker container/image - ensure correct target";
        }
        if (lower.contains("kill ") && !lower.contains("-l")) {
            return "Killing processes - ensure correct PID";
        }

        return null;
    }

    /**
     * Sanitize a command by removing potentially dangerous parts.
     * Returns a safer version of the command.
     */
    public String sanitizeCommand(String command) {
        if (command == null) {
            return null;
        }

        // Remove any shell command chaining that could be injection
        String sanitized = command
                .replaceAll(";\\s*rm\\s+", "; echo 'rm blocked' # ")
                .replaceAll("\\|\\s*rm\\s+", "| echo 'rm blocked' # ")
                .replaceAll("`[^`]*rm[^`]*`", "echo 'command substitution blocked'")
                .replaceAll("\\$\\([^)]*rm[^)]*\\)", "echo 'command substitution blocked'");

        return sanitized;
    }

    public record ValidationResult(
            boolean permitted,
            String denialReason,
            String warning
    ) {
        public static ValidationResult allowed() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult allowedWithWarning(String warning) {
            return new ValidationResult(true, null, warning);
        }

        public static ValidationResult denied(String reason) {
            return new ValidationResult(false, reason, null);
        }

        public boolean hasWarning() {
            return warning != null;
        }
    }
}
