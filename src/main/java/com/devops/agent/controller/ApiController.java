package com.devops.agent.controller;

import com.devops.agent.agent.BaseAgent;
import com.devops.agent.agent.MultiAgentCoordinator;
import com.devops.agent.common.PageRequest;
import com.devops.agent.common.PageResult;
import com.devops.agent.common.Result;
import com.devops.agent.entity.Server;
import com.devops.agent.entity.Task;
import com.devops.agent.service.AsyncTaskService;
import com.devops.agent.service.IntentRecognizer;
import com.devops.agent.service.ServerService;
import com.devops.agent.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ServerService serverService;
    private final TaskService taskService;
    private final AsyncTaskService asyncTaskService;
    private final IntentRecognizer intentRecognizer;
    private final MultiAgentCoordinator multiAgentCoordinator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Server endpoints =====

    @PostMapping("/servers")
    public Result<Server> addServer(@RequestBody Server server) {
        return Result.success(serverService.addServer(server));
    }

    @PutMapping("/servers")
    public Result<Server> updateServer(@RequestBody Server server) {
        return Result.success(serverService.updateServer(server));
    }

    @DeleteMapping("/servers/{id}")
    public Result<Void> deleteServer(@PathVariable Long id) {
        serverService.deleteServer(id);
        return Result.success();
    }

    @GetMapping("/servers")
    public Result<List<Server>> listServers() {
        return Result.success(serverService.listServers());
    }

    @GetMapping("/servers/{id}")
    public Result<Server> getServer(@PathVariable Long id) {
        return Result.success(serverService.getServer(id));
    }

    @PostMapping("/servers/{id}/test")
    public Result<Boolean> testConnection(@PathVariable Long id) {
        boolean ok = serverService.testConnection(id);
        return Result.success(ok);
    }

    /**
     * 对指定已登记服务器执行快速诊断（参数为 serverId，语义清晰）。
     */
    @PostMapping("/servers/{serverId}/diagnostics")
    public Result<Task> runDiagnosticsByServer(@PathVariable Long serverId) {
        return Result.success(taskService.runDiagnostics(serverId));
    }

    // ===== Task endpoints =====

    /**
     * Submit a task for synchronous execution (waits for completion).
     */
    @PostMapping("/tasks")
    public Result<Task> submitTask(@RequestBody Map<String, Object> request) {
        String userInput = (String) request.get("userInput");
        Long serverId = ((Number) request.get("serverId")).longValue();
        String agentType = (String) request.getOrDefault("agentType", "command_executor");

        // Auto-detect agent type if not specified (same as async endpoint)
        if ("auto".equals(agentType)) {
            IntentRecognizer.IntentResult intent = intentRecognizer.recognizeIntent(userInput);
            agentType = intent.agentType();
        }

        Task task = taskService.executeTaskSync(userInput, serverId, agentType);
        return Result.success(task);
    }

    /**
     * Submit a task for asynchronous execution (returns immediately).
     */
    @PostMapping("/tasks/async")
    public Result<Task> submitTaskAsync(@RequestBody Map<String, Object> request) {
        String userInput = (String) request.get("userInput");
        Long serverId = ((Number) request.get("serverId")).longValue();
        String agentType = (String) request.getOrDefault("agentType", "auto");

        // Auto-detect agent type if not specified
        if ("auto".equals(agentType)) {
            IntentRecognizer.IntentResult intent = intentRecognizer.recognizeIntent(userInput);
            agentType = intent.agentType();
        }

        Task task = taskService.submitTask(userInput, serverId, agentType);
        asyncTaskService.executeTaskAsync(task.getId(), agentType, serverId);
        return Result.success(task);
    }

    /**
     * Submit a multi-agent workflow task.
     */
    @PostMapping("/tasks/workflow")
    public Result<Task> submitWorkflowTask(@RequestBody Map<String, Object> request) {
        String userInput = (String) request.get("userInput");
        Long serverId = ((Number) request.get("serverId")).longValue();

        Task task = taskService.submitTask(userInput, serverId, "multi_agent");
        // Execute multi-agent workflow asynchronously
        taskService.executeMultiAgentWorkflowAsync(task.getId(), serverId);
        return Result.success(task);
    }

    /**
     * Cancel a running task.
     */
    @PostMapping("/tasks/{id}/cancel")
    public Result<Boolean> cancelTask(@PathVariable Long id) {
        boolean cancelled = asyncTaskService.cancelTask(id);
        return Result.success(cancelled);
    }

    /**
     * Get task execution progress.
     */
    @GetMapping("/tasks/{id}/progress")
    public Result<AsyncTaskService.TaskProgress> getTaskProgress(@PathVariable Long id) {
        return Result.success(asyncTaskService.getTaskProgress(id));
    }

    /**
     * Recognize intent from user input (for testing).
     */
    @PostMapping("/intent/recognize")
    public Result<IntentRecognizer.IntentResult> recognizeIntent(@RequestBody Map<String, String> request) {
        String userInput = request.get("userInput");
        IntentRecognizer.IntentResult result = intentRecognizer.recognizeIntent(userInput);
        return Result.success(result);
    }

    /**
     * @deprecated 为兼容旧文档保留；语义上为 {@code serverId}，建议改用 {@code POST /api/servers/{serverId}/diagnostics}。
     */
    @Deprecated
    @PostMapping("/tasks/{serverId}/diagnostics")
    public Result<Task> runDiagnosticsLegacy(@PathVariable Long serverId) {
        Task task = taskService.runDiagnostics(serverId);
        return Result.success(task);
    }

    @GetMapping("/tasks")
    public Result<PageResult<Task>> listTasks(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) String agentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setSize(size);
        return Result.success(taskService.listTasks(status, serverId, agentType, pageRequest));
    }

    @GetMapping("/tasks/{id}")
    public Result<TaskService.TaskDetail> getTaskDetail(@PathVariable Long id) {
        return Result.success(taskService.getTaskDetail(id));
    }

    // ===== Agent endpoints =====

    @GetMapping("/agents")
    public Result<List<com.devops.agent.entity.Agent>> listAgents() {
        return Result.success(taskService.listAgents());
    }

    /**
     * 返回集成的课设 linux-agent-skills 工具目录（JSON，与远端 runbook 对应）。
     */
    @GetMapping("/linux-skills/catalog")
    public Result<JsonNode> linuxSkillsCatalog() {
        try {
            ClassPathResource res = new ClassPathResource("linux-skills/tools_catalog.json");
            try (InputStream in = res.getInputStream()) {
                JsonNode node = objectMapper.readTree(in);
                return Result.success(node);
            }
        } catch (Exception e) {
            return Result.error("无法加载 tools_catalog.json: " + e.getMessage());
        }
    }
}
