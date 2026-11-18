package com.luckyflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LuckyFlow 应用程序启动入口。
 *
 * <p>应用模块负责组装各领域模块，并承载运行时 Spring Boot 上下文。</p>
 */
@SpringBootApplication
public class LuckyFlowApplication {

    /**
     * 启动 LuckyFlow Spring Boot 应用程序。
     *
     * @param args 传递给 Spring Boot 的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LuckyFlowApplication.class, args);
    }
}
