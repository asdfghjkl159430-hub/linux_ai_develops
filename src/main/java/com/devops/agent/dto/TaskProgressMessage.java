package com.devops.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for task progress updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressMessage {

    private Long taskId;
    private String event;
    private int step;
    private String command;
    private String output;
    private int status;
    private String message;
    private LocalDateTime timestamp;

    // Event types
    public static final String EVENT_STARTED = "TASK_STARTED";
    public static final String EVENT_STEP_START = "STEP_START";
    public static final String EVENT_STEP_COMPLETE = "STEP_COMPLETE";
    public static final String EVENT_STEP_FAILED = "STEP_FAILED";
    public static final String EVENT_COMPLETED = "TASK_COMPLETED";
    public static final String EVENT_FAILED = "TASK_FAILED";
    public static final String EVENT_CANCELLED = "TASK_CANCELLED";

    public static TaskProgressMessage started(Long taskId, String message) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_STARTED)
                .status(1)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage stepStart(Long taskId, int step, String command) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_STEP_START)
                .step(step)
                .command(command)
                .status(1)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage stepComplete(Long taskId, int step, String command, String output) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_STEP_COMPLETE)
                .step(step)
                .command(command)
                .output(truncate(output, 1000))
                .status(1)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage stepFailed(Long taskId, int step, String command, String error) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_STEP_FAILED)
                .step(step)
                .command(command)
                .message(error)
                .status(2)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage completed(Long taskId, String summary) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_COMPLETED)
                .status(2)
                .message(summary)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage failed(Long taskId, String error) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_FAILED)
                .status(3)
                .message(error)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TaskProgressMessage cancelled(Long taskId) {
        return TaskProgressMessage.builder()
                .taskId(taskId)
                .event(EVENT_CANCELLED)
                .status(4)
                .message("Task cancelled by user")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
