package com.autohr.modules.interview.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class InterviewRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsKnowledgeContentBeyondDatabaseLimit() {
        KnowledgeItemSaveRequest request = new KnowledgeItemSaveRequest();
        request.setKnowledgeBaseId(1L);
        request.setKnowledgePoint("Java");
        request.setKnowledgeContent("x".repeat(5001));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsLlmPromptBeyondDatabaseLimit() {
        LlmConfigSaveRequest request = new LlmConfigSaveRequest();
        request.setConfigName("interviewer");
        request.setModelRole("INTERVIEWER");
        request.setBaseUrl("https://example.invalid/v1");
        request.setModelName("model");
        request.setPromptTemplate("x".repeat(5001));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsUnboundedKnowledgeWeight() {
        JobKnowledgeWeightSaveRequest request = new JobKnowledgeWeightSaveRequest();
        request.setJobId(1L);
        request.setKnowledgeBaseId(1L);
        request.setWeight(1001);

        assertFalse(validator.validate(request).isEmpty());
    }
}
