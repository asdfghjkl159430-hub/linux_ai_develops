package com.devops.agent.mapper;

import com.devops.agent.entity.Server;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ServerMapper {

    @Insert("INSERT INTO server(name, host, port, username, password, os_type, tags, status) " +
            "VALUES(#{name}, #{host}, #{port}, #{username}, #{password}, #{osType}, #{tags}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Server server);

    @Update("UPDATE server SET name=#{name}, host=#{host}, port=#{port}, username=#{username}, " +
            "password=#{password}, os_type=#{osType}, tags=#{tags}, status=#{status} WHERE id=#{id}")
    int update(Server server);

    @Delete("DELETE FROM server WHERE id=#{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM server WHERE id=#{id}")
    Server selectById(Long id);

    @Select("SELECT * FROM server WHERE status=1 ORDER BY created_at DESC")
    List<Server> selectAll();

    @Select("SELECT * FROM server WHERE host=#{host} AND port=#{port}")
    Server selectByHostAndPort(String host, Integer port);
}
