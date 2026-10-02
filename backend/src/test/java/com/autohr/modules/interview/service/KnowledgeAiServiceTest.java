package com.autohr.modules.interview.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.interview.entity.InterviewKnowledgeBase;
import com.autohr.modules.interview.mapper.InterviewKnowledgeBaseMapper;
import com.autohr.modules.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeAiServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final InterviewKnowledgeBaseMapper bases = mock(InterviewKnowledgeBaseMapper.class);
    private final InterviewService interview = mock(InterviewService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final KnowledgeAiService service = new KnowledgeAiService(new TeachingMaterialReader(), config, bases, interview, json);
    private void existingBase() {
        var base = new InterviewKnowledgeBase(); base.setId(7L); base.setKnowledgeBaseName("程序设计");
        when(bases.selectById(7L)).thenReturn(base);
    }

    @Test void parsesFencedJsonAndRejectsMalformedOrOversizedItems() throws Exception {
        assertEquals("循环", service.parse("```json\n{\"items\":[{\"knowledgePoint\":\"循环\",\"knowledgeContent\":\"教学内容\"}]}\n```").get(0).knowledgePoint());
        assertThrows(BusinessException.class, () -> service.parse("{\"items\":[{\"knowledgePoint\":123,\"knowledgeContent\":\"内容\"}]}"));
        assertThrows(BusinessException.class, () -> service.parse("{\"items\":{}}"));
        assertThrows(BusinessException.class, () -> service.parse(json.writeValueAsString(Map.of("items", List.of(new KnowledgeAiService.Item("知识点", "a".repeat(5001)))))));
    }

    @Test void validatesEntireBatchBeforeWritingAndUsesExistingSaveRules() {
        existingBase();
        assertThrows(BusinessException.class, () -> service.save(7L, List.of(new KnowledgeAiService.Item("循环", "内容"), new KnowledgeAiService.Item("", "无效"))));
        verifyNoInteractions(interview);
        assertEquals(1, service.save(7L, List.of(new KnowledgeAiService.Item(" 循环 ", " 内容 "))));
        verify(interview).saveKnowledgeItem(argThat(item -> item.getId() == null && item.getKnowledgeBaseId().equals(7L)
                && item.getKnowledgePoint().equals("循环") && item.getKnowledgeContent().equals("内容") && item.getStatus() == 1));
    }

    @Test void processesAllChunksAndMergesRepeatedPointsWithoutWritingDuringGeneration() throws Exception {
        existingBase();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int index = calls.incrementAndGet();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(requestBody.contains("teachingMaterials"));
            String content = json.writeValueAsString(Map.of("items", List.of(new KnowledgeAiService.Item("循环", "材料内容" + index))));
            byte[] response = json.writeValueAsBytes(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content)))));
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            when(config.loadConfig("SCHOOL_LLM_BASE_URL", "SCHOOL_LLM_API_KEY", "SCHOOL_LLM_MODEL"))
                    .thenReturn(Map.of("SCHOOL_LLM_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "SCHOOL_LLM_API_KEY", "test", "SCHOOL_LLM_MODEL", "test-model"));
            var result = service.generate(7L, List.of(new MockMultipartFile("files", "大纲.txt", "text/plain", "课".repeat(24001).getBytes(StandardCharsets.UTF_8))));
            assertEquals(3, calls.get()); assertEquals(1, result.size());
            assertEquals("材料内容1\n材料内容2\n材料内容3", result.get(0).knowledgeContent());
            verifyNoInteractions(interview);
        } finally { server.stop(0); }
    }

    @Test void rejectsMissingModelConfigAndDeletedTarget() {
        assertThrows(BusinessException.class, () -> service.generate(7L, List.of()));
        existingBase();
        when(config.loadConfig("SCHOOL_LLM_BASE_URL", "SCHOOL_LLM_API_KEY", "SCHOOL_LLM_MODEL")).thenReturn(Map.of());
        assertThrows(BusinessException.class, () -> service.generate(7L, List.of(new MockMultipartFile("files", "大纲.txt", "text/plain", new byte[]{1}))));
        verifyNoInteractions(interview);
    }

    @Test void rollsBackEntireBatchWhenAnExistingSaveRuleRejectsAnItem(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        existingBase();
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("knowledge.db"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE test_items (point TEXT)");
        var writes = new AtomicInteger();
        when(interview.saveKnowledgeItem(any())).thenAnswer(invocation -> {
            com.autohr.modules.interview.dto.KnowledgeItemSaveRequest request = invocation.getArgument(0);
            jdbc.update("INSERT INTO test_items (point) VALUES (?)", request.getKnowledgePoint());
            if (writes.incrementAndGet() == 2) throw new BusinessException("知识点未通过现有校验");
            return null;
        });
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (KnowledgeAiService) factory.getProxy();
        assertThrows(BusinessException.class, () -> transactional.save(7L, List.of(
                new KnowledgeAiService.Item("循环", "内容"), new KnowledgeAiService.Item("分支", "内容"))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_items", Integer.class));
    }
}
