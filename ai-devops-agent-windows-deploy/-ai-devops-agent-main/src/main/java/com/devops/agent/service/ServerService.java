package com.devops.agent.service;

import com.devops.agent.entity.Server;
import com.devops.agent.mapper.ServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerMapper serverMapper;
    private final SshService sshService;

    public Server addServer(Server server) {
        if (server.getPort() == null) {
            server.setPort(22);
        }
        if (server.getOsType() == null) {
            server.setOsType("Linux");
        }
        if (server.getStatus() == null) {
            server.setStatus(1);
        }
        serverMapper.insert(server);
        return server;
    }

    public Server updateServer(Server server) {
        serverMapper.update(server);
        return serverMapper.selectById(server.getId());
    }

    public void deleteServer(Long id) {
        serverMapper.deleteById(id);
    }

    public Server getServer(Long id) {
        return serverMapper.selectById(id);
    }

    public List<Server> listServers() {
        return serverMapper.selectAll();
    }

    /**
     * Test SSH connectivity to a server.
     */
    public boolean testConnection(Long serverId) {
        Server server = serverMapper.selectById(serverId);
        if (server == null) {
            return false;
        }
        SshResult result = sshService.executeCommand(server, "echo 'connection test ok'");
        return result.isSuccess();
    }
}
