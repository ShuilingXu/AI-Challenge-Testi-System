package com.autohr;

import com.autohr.config.EnvironmentFileBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoHrApplication {
    public static void main(String[] args) {
        EnvironmentFileBootstrap.ensureDefaultEnvFile();
        SpringApplication.run(AutoHrApplication.class, args);
    }
}
