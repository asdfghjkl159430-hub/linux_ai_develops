package com.devops.agent.mapper;

import com.devops.agent.entity.Agent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AgentMapper {

    @Select("SELECT * FROM agent WHERE agent_type=#{agentType} AND status=1")
    Agent selectByType(String agentType);

    @Select("SELECT * FROM agent WHERE status=1")
    List<Agent> selectAll();
}
