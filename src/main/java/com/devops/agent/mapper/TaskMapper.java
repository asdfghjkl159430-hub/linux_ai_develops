package com.devops.agent.mapper;

import com.devops.agent.entity.Task;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TaskMapper {

    @Insert("INSERT INTO task(user_input, intent, server_id, status, agent_type, result_summary, error_message, started_at) " +
            "VALUES(#{userInput}, #{intent}, #{serverId}, #{status}, #{agentType}, #{resultSummary}, #{errorMessage}, #{startedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Task task);

    @Update("UPDATE task SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE task SET status=#{status}, result_summary=#{resultSummary}, " +
            "error_message=#{errorMessage}, finished_at=NOW() WHERE id=#{id}")
    int updateResult(Task task);

    @Select("SELECT * FROM task WHERE id=#{id}")
    Task selectById(Long id);

    @Select("SELECT * FROM task WHERE server_id=#{serverId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Task> selectByServerId(@Param("serverId") Long serverId, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT * FROM task " +
            "<where>" +
            "  <if test='status != null'> AND status=#{status} </if>" +
            "  <if test='serverId != null'> AND server_id=#{serverId} </if>" +
            "  <if test='agentType != null and agentType != \"\"'> AND agent_type=#{agentType} </if>" +
            "</where>" +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Task> selectByCondition(@Param("status") Integer status,
                                 @Param("serverId") Long serverId,
                                 @Param("agentType") String agentType,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM task " +
            "<where>" +
            "  <if test='status != null'> AND status=#{status} </if>" +
            "  <if test='serverId != null'> AND server_id=#{serverId} </if>" +
            "  <if test='agentType != null and agentType != \"\"'> AND agent_type=#{agentType} </if>" +
            "</where>" +
            "</script>")
    long countByCondition(@Param("status") Integer status,
                          @Param("serverId") Long serverId,
                          @Param("agentType") String agentType);
}
