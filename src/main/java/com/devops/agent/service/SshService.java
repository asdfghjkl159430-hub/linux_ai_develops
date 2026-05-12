package com.devops.agent.service;

import com.devops.agent.entity.Server;

public interface SshService {
    /**
     * Execute a single command on the given server.
     * Returns the combined stdout+stderr and exit code.
     */
    SshResult executeCommand(Server server, String command);

    /**
     * Execute multiple commands sequentially on the same server,
     * reusing the SSH connection.
     */
    java.util.List<SshResult> executeCommands(Server server, java.util.List<String> commands);
}
