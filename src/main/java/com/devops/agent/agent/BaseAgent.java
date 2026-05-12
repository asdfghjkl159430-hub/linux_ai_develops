package com.devops.agent.agent;

import com.devops.agent.entity.Server;

import java.util.List;

/**
 * Base interface for all AI agents.
 */
public interface BaseAgent {

    /**
     * Returns the agent type identifier (matches agent_type in DB).
     */
    String getAgentType();

    /**
     * Execute the agent's logic based on user input and server context.
     * Returns a list of commands to execute.
     */
    AgentPlan plan(String userInput, Server server);

    /**
     * Given the previous command output, decide the next action.
     * Returns null if the task is complete.
     */
    AgentPlan nextStep(String previousOutput, String previousReasoning);

    /**
     * Analyze the final result and produce a summary.
     */
    String summarize(List<String> commandOutputs);
}
