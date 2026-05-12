package com.devops.agent.controller;

import com.devops.agent.common.Result;
import com.devops.agent.mapper.AgentMapper;
import com.devops.agent.mapper.ServerMapper;
import com.devops.agent.mapper.TaskMapper;
import com.devops.agent.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * System health check and monitoring endpoints.
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final ServerMapper serverMapper;
    private final TaskMapper taskMapper;
    private final AgentMapper agentMapper;
    private final AsyncTaskService asyncTaskService;

    /**
     * Basic health check endpoint.
     */
    @GetMapping("/health")
    public Result<HealthStatus> health() {
        HealthStatus status = new HealthStatus(
                "UP",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "AI DevOps Agent",
                "1.0.0"
        );
        return Result.success(status);
    }

    /**
     * Detailed system status.
     */
    @GetMapping("/status")
    public Result<SystemStatus> status() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        // Count running tasks
        int runningTasks = asyncTaskService.getRunningTasksCount();

        SystemStatus status = new SystemStatus(
                "UP",
                runtime.getUptime() / 1000, // seconds
                memory.getHeapMemoryUsage().getUsed() / 1024 / 1024, // MB
                memory.getHeapMemoryUsage().getMax() / 1024 / 1024, // MB
                Thread.activeCount(),
                runningTasks,
                serverMapper.selectAll().size(),
                agentMapper.selectAll().size()
        );
        return Result.success(status);
    }

    /**
     * System metrics for monitoring.
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // JVM metrics
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heap_used_mb", memory.getHeapMemoryUsage().getUsed() / 1024 / 1024);
        jvm.put("heap_max_mb", memory.getHeapMemoryUsage().getMax() / 1024 / 1024);
        jvm.put("heap_usage_percent", 
                (double) memory.getHeapMemoryUsage().getUsed() / memory.getHeapMemoryUsage().getMax() * 100);
        jvm.put("available_processors", runtime.availableProcessors());
        jvm.put("total_threads", Thread.activeCount());
        metrics.put("jvm", jvm);

        // Database metrics
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("servers", serverMapper.selectAll().size());
        db.put("agents", agentMapper.selectAll().size());
        metrics.put("database", db);

        // Task metrics
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("running", asyncTaskService.getRunningTasksCount());
        metrics.put("tasks", tasks);

        return Result.success(metrics);
    }

    /**
     * List all registered agents.
     */
    @GetMapping("/agents")
    public Result<List<AgentInfo>> listAgents() {
        List<AgentInfo> agents = agentMapper.selectAll().stream()
                .map(a -> new AgentInfo(
                        a.getAgentType(),
                        a.getName(),
                        a.getDescription(),
                        a.getStatus() == 1
                ))
                .toList();
        return Result.success(agents);
    }

    // DTOs
    public record HealthStatus(
            String status,
            String timestamp,
            String application,
            String version
    ) {}

    public record SystemStatus(
            String status,
            long uptimeSeconds,
            long heapUsedMb,
            long heapMaxMb,
            int threadCount,
            int runningTasks,
            int serverCount,
            int agentCount
    ) {}

    public record AgentInfo(
            String type,
            String name,
            String description,
            boolean active
    ) {}
}
