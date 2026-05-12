package com.devops.agent.service;

import com.devops.agent.entity.Server;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SshServiceImpl implements SshService {

    private static final int DEFAULT_SESSION_TIMEOUT_MS = 30000;
    /** 单条命令从建连到收集完输出的上限，避免 HTTP 线程永久挂起 */
    private static final int COMMAND_COMPLETION_TIMEOUT_MS = 120_000;

    @Override
    public SshResult executeCommand(Server server, String command) {
        Session session = null;
        ChannelExec channel = null;
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            session.setPassword(server.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(DEFAULT_SESSION_TIMEOUT_MS);
            session.connect(10000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(errBuf);

            InputStream out = channel.getInputStream();
            channel.connect(5000);

            String output = readExecChannelUntilClose(out, channel, COMMAND_COMPLETION_TIMEOUT_MS);
            int exitCode = channel.getExitStatus();
            String errorOutput = errBuf.toString(StandardCharsets.UTF_8).trim();

            log.info("SSH command executed on {}:{} (exit={}) -> {}",
                    server.getHost(), server.getPort(), exitCode, command);

            if (exitCode == 0) {
                return SshResult.success(output.trim());
            }
            return SshResult.failure(exitCode, errorOutput.isEmpty() ? "non-zero exit code: " + exitCode : errorOutput);
        } catch (JSchException e) {
            log.error("SSH connection failed: {}@{}:{}", server.getUsername(), server.getHost(), server.getPort(), e);
            return SshResult.failure(-1, "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("SSH command execution failed", e);
            return SshResult.failure(-1, "command execution error: " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @Override
    public List<SshResult> executeCommands(Server server, List<String> commands) {
        Session session = null;
        List<SshResult> results = new ArrayList<>();
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            session.setPassword(server.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(DEFAULT_SESSION_TIMEOUT_MS);
            session.connect(10000);

            for (String command : commands) {
                ChannelExec channel = null;
                ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
                try {
                    channel = (ChannelExec) session.openChannel("exec");
                    channel.setCommand(command);
                    channel.setErrStream(errBuf);

                    InputStream out = channel.getInputStream();
                    channel.connect(5000);

                    String output = readExecChannelUntilClose(out, channel, COMMAND_COMPLETION_TIMEOUT_MS);
                    int exitCode = channel.getExitStatus();
                    String errorOutput = errBuf.toString(StandardCharsets.UTF_8).trim();

                    results.add(exitCode == 0
                            ? SshResult.success(output.trim())
                            : SshResult.failure(exitCode, errorOutput));
                } catch (Exception e) {
                    log.error("Failed to execute command: {}", command, e);
                    results.add(SshResult.failure(-1, e.getMessage()));
                } finally {
                    if (channel != null && channel.isConnected()) {
                        channel.disconnect();
                    }
                }
            }
        } catch (JSchException e) {
            log.error("SSH connection failed", e);
            results.add(SshResult.failure(-1, "SSH connection failed: " + e.getMessage()));
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return results;
    }

    /**
     * JSch 官方推荐的 exec 读法：按 available 轮询 stdin，避免 BufferedReader 在无换行/未关流时永久阻塞。
     */
    private static String readExecChannelUntilClose(InputStream in, Channel channel, int maxWaitMs)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        byte[] tmp = new byte[4096];
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (true) {
            while (in.available() > 0) {
                int n = in.read(tmp);
                if (n < 0) {
                    break;
                }
                sb.append(new String(tmp, 0, n, StandardCharsets.UTF_8));
            }
            if (channel.isClosed()) {
                while (in.available() > 0) {
                    int n = in.read(tmp);
                    if (n < 0) {
                        break;
                    }
                    sb.append(new String(tmp, 0, n, StandardCharsets.UTF_8));
                }
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("Timed out waiting for SSH exec channel to finish (>" + maxWaitMs + " ms)");
            }
            Thread.sleep(50);
        }
        return sb.toString();
    }
}
