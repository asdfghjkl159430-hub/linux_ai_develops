package com.devops.agent.agent;

import lombok.Data;
import java.util.List;

@Data
public class AgentPlan {
    private String command;
    private String explanation;
    private List<String> multiCommands;

    public static AgentPlan single(String command, String explanation) {
        AgentPlan plan = new AgentPlan();
        plan.setCommand(command);
        plan.setExplanation(explanation);
        return plan;
    }

    public static AgentPlan multi(List<String> commands) {
        AgentPlan plan = new AgentPlan();
        plan.setMultiCommands(commands);
        return plan;
    }
}
