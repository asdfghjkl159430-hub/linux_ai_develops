package com.devops.agent.service;

import com.devops.agent.dto.TaskProgressMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for broadcasting task progress updates via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProgressBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_TASK_PROGRESS = "/topic/task/";
    private static final String TOPIC_ALL_TASKS = "/topic/tasks";

    /**
     * Broadcast progress to all subscribers and task-specific topic.
     */
    public void broadcast(TaskProgressMessage message) {
        log.debug("Broadcasting task progress: taskId={}, event={}", message.getTaskId(), message.getEvent());
        
        // Send to task-specific topic
        messagingTemplate.convertAndSend(TOPIC_TASK_PROGRESS + message.getTaskId(), message);
        
        // Also broadcast to global task topic for dashboard
        messagingTemplate.convertAndSend(TOPIC_ALL_TASKS, message);
    }

    /**
     * Send progress to specific task subscribers only.
     */
    public void sendToTask(Long taskId, TaskProgressMessage message) {
        messagingTemplate.convertAndSend(TOPIC_TASK_PROGRESS + taskId, message);
    }
}
