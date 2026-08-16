package com.autohr.modules.interview.config;

import com.autohr.common.security.SecretValueCipher;
import com.autohr.modules.interview.entity.InterviewLlmConfig;
import com.autohr.modules.interview.mapper.InterviewLlmConfigMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
public class LlmSecretMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LlmSecretMigrationRunner.class);

    private final InterviewLlmConfigMapper llmConfigMapper;
    private final SecretValueCipher secretValueCipher;

    @Override
    public void run(String... args) {
        int migrated = 0;
        for (InterviewLlmConfig config : llmConfigMapper.selectList(null)) {
            String stored = config.getApiKey();
            if (stored == null || stored.isBlank() || secretValueCipher.isEncrypted(stored)) {
                continue;
            }
            int updated = llmConfigMapper.update(null, new LambdaUpdateWrapper<InterviewLlmConfig>()
                    .eq(InterviewLlmConfig::getId, config.getId())
                    .eq(InterviewLlmConfig::getApiKey, stored)
                    .set(InterviewLlmConfig::getApiKey, secretValueCipher.encrypt(stored)));
            migrated += updated;
        }
        if (migrated > 0) {
            log.info("Encrypted {} legacy LLM API key configuration(s)", migrated);
        }
    }
}
