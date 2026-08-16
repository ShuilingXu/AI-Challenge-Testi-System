package com.autohr.modules.interview.controller;

import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.interview.dto.InterviewProcessTemplateSaveRequest;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.JobKnowledgeWeightSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeBaseSaveRequest;
import com.autohr.modules.interview.dto.KnowledgeItemSaveRequest;
import com.autohr.modules.interview.dto.LlmConfigSaveRequest;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
import com.autohr.modules.interview.service.InterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewControllerAuditTest {

    @Mock InterviewService interviewService;
    @Mock AuthService authService;
    @Mock AuditLogService auditLogService;
    @Mock S3ObjectStorageService s3ObjectStorageService;
    @Mock Authentication authentication;

    @InjectMocks InterviewController controller;

    private SessionUserVO operator;

    @BeforeEach
    void setUpOperator() {
        operator = new SessionUserVO();
        operator.setId(9L);
        operator.setDisplayName("审计员");
        operator.setRoleCode("IT_ADMIN");
        when(authentication.getName()).thenReturn("itadmin");
        when(authService.loadUserByUsername("itadmin")).thenReturn(operator);
    }

    @Test
    void auditsInterviewConfigurationAndProcessMutationsWithExplicitTargets() {
        KnowledgeBaseSaveRequest knowledgeRequest = new KnowledgeBaseSaveRequest();
        InterviewVO knowledge = result(11L);
        knowledge.setKnowledgeBaseName("Java");
        when(interviewService.saveKnowledgeBase(knowledgeRequest)).thenReturn(knowledge);

        KnowledgeItemSaveRequest itemRequest = new KnowledgeItemSaveRequest();
        itemRequest.setKnowledgeBaseId(11L);
        itemRequest.setKnowledgePoint("Streams");
        itemRequest.setKnowledgeContent("Java streams");
        InterviewVO item = result(15L);
        item.setKnowledgeBaseId(11L);
        item.setKnowledgePoint("Streams");
        when(interviewService.saveKnowledgeItem(itemRequest)).thenReturn(item);

        JobKnowledgeWeightSaveRequest weightRequest = new JobKnowledgeWeightSaveRequest();
        weightRequest.setJobId(21L);
        weightRequest.setKnowledgeBaseId(11L);
        weightRequest.setWeight(80);
        InterviewVO weight = result(16L);
        weight.setJobId(21L);
        weight.setKnowledgeBaseId(11L);
        weight.setWeight(80);
        when(interviewService.saveJobKnowledgeWeight(weightRequest)).thenReturn(weight);

        LlmConfigSaveRequest llmRequest = new LlmConfigSaveRequest();
        llmRequest.setApiKey("secret-that-must-not-be-logged");
        InterviewVO llm = result(12L);
        llm.setConfigName("scorer");
        llm.setModelRole("SCORER");
        when(interviewService.saveLlmConfig(llmRequest)).thenReturn(llm);

        InterviewProcessTemplateSaveRequest templateRequest = new InterviewProcessTemplateSaveRequest();
        InterviewVO template = result(13L);
        template.setTemplateName("研发流程");
        when(interviewService.saveProcessTemplate(templateRequest)).thenReturn(template);

        StartInterviewProcessRequest processRequest = new StartInterviewProcessRequest();
        processRequest.setRecruitmentCandidateId(31L);
        processRequest.setTemplateId(13L);
        InterviewVO process = result(14L);
        when(interviewService.startInterviewProcess(processRequest)).thenReturn(process);

        controller.saveKnowledgeBase(authentication, knowledgeRequest);
        controller.deleteKnowledgeBase(authentication, 11L);
        controller.saveKnowledgeItem(authentication, itemRequest);
        controller.importKnowledgeItems(authentication, 11L, null);
        controller.deleteKnowledgeItem(authentication, 15L);
        controller.saveJobKnowledgeWeight(authentication, weightRequest);
        controller.deleteJobKnowledgeWeight(authentication, 16L);
        controller.saveLlmConfig(authentication, llmRequest);
        controller.deleteLlmConfig(authentication, 12L);
        controller.saveProcessTemplate(authentication, templateRequest);
        controller.deleteProcessTemplate(authentication, 13L, 2);
        controller.startProcess(authentication, processRequest);

        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "CREATE_KNOWLEDGE_ITEM", "KNOWLEDGE_ITEM", "15", "knowledgeBaseId=11, point=Streams");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "IMPORT_KNOWLEDGE_ITEMS", "KNOWLEDGE_BASE", "11", "imported=0");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "DELETE_KNOWLEDGE_ITEM", "KNOWLEDGE_ITEM", "15", "删除知识条目");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "CREATE_JOB_KNOWLEDGE_WEIGHT", "JOB_KNOWLEDGE_WEIGHT", "16",
                "jobId=21, knowledgeBaseId=11, weight=80");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "DELETE_JOB_KNOWLEDGE_WEIGHT", "JOB_KNOWLEDGE_WEIGHT", "16", "删除岗位知识权重");

        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "CREATE_KNOWLEDGE_BASE", "KNOWLEDGE_BASE", "11", "Java");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "DELETE_KNOWLEDGE_BASE", "KNOWLEDGE_BASE", "11", "删除知识库");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "CREATE_LLM_CONFIG", "LLM_CONFIG", "12", "configName=scorer, modelRole=SCORER");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "DELETE_LLM_CONFIG", "LLM_CONFIG", "12", "删除 LLM 配置");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "CREATE_PROCESS_TEMPLATE", "PROCESS_TEMPLATE", "13", "研发流程");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "DELETE_PROCESS_TEMPLATE", "PROCESS_TEMPLATE", "13", "version=2");
        verify(auditLogService).log(9L, "审计员", "IT_ADMIN", "INTERVIEW",
                "START_INTERVIEW_PROCESS", "INTERVIEW_PROCESS", "14", "candidateId=31, templateId=13");

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, org.mockito.Mockito.atLeastOnce()).log(any(), any(), any(), any(), any(), any(), any(), detail.capture());
        assertFalse(detail.getAllValues().stream().anyMatch(value -> value.contains(llmRequest.getApiKey())));
    }

    private InterviewVO result(Long id) {
        InterviewVO result = new InterviewVO();
        result.setId(id);
        return result;
    }
}
