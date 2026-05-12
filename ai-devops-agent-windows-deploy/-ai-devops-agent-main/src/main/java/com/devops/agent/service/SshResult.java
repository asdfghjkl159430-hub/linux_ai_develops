package com.devops.agent.service;

public record SshResult(String output, int exitCode, String error) {

    public static SshResult success(String output) {
        return new SshResult(output, 0, "");
    }

    public static SshResult failure(int exitCode, String error) {
        return new SshResult("", exitCode, error);
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
