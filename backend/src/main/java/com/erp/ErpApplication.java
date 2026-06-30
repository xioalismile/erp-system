package com.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.erp.**.mapper")
@EnableScheduling
public class ErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
        System.out.println("""

                ===================================================
                  智能ERP系统 启动成功
                  AI渠道: 通义千问 / DeepSeek (可配置)
                  访问地址: http://localhost:8080
                ===================================================
                """);
    }
}
