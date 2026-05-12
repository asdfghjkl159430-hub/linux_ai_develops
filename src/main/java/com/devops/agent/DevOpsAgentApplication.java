package com.devops.agent;

import com.devops.agent.config.LinuxSkillsProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.devops.agent")
@MapperScan("com.devops.agent.mapper")
@EnableConfigurationProperties(LinuxSkillsProperties.class)
public class DevOpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevOpsAgentApplication.class, args);
    }
}
