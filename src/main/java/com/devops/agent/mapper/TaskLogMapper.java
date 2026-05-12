package com.devops.agent.mapper;

import com.devops.agent.entity.TaskLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TaskLogMapper {

    @Insert("INSERT INTO task_log(task_id, step, command, output, exit_code, ai_reasoning, status) " +
            "VALUES(#{taskId}, #{step}, #{command}, #{output}, #{exitCode}, #{aiReasoning}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskLog taskLog);

    @Select("SELECT * FROM task_log WHERE task_id=#{taskId} ORDER BY step ASC")
    List<TaskLog> selectByTaskId(Long taskId);

    @Delete("DELETE FROM task_log WHERE task_id=#{taskId}")
    int deleteByTaskId(Long taskId);
}
