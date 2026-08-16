package com.autohr.modules.recruitment.service.impl;

import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.modules.interview.entity.InterviewProcess;
import com.autohr.modules.interview.entity.InterviewProcessStage;
import com.autohr.modules.interview.entity.InterviewVideoSession;
import com.autohr.modules.interview.mapper.InterviewProcessStageMapper;
import com.autohr.modules.interview.mapper.InterviewVideoSessionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceImplTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, InterviewProcessStage.class);
        TableInfoHelper.initTableInfo(assistant, InterviewVideoSession.class);
    }

    @Mock
    InterviewProcessStageMapper interviewProcessStageMapper;

    @Mock
    InterviewVideoSessionMapper interviewVideoSessionMapper;

    @Mock
    S3ObjectStorageService s3ObjectStorageService;

    @InjectMocks
    RecruitmentServiceImpl service;

    @Test
    void candidateRecordingCleanupIncludesEveryArtifactAndDeduplicatesAliases() {
        InterviewProcess process = new InterviewProcess();
        process.setId(42L);
        process.setAiRecordingFileName("ai.webm");
        InterviewProcessStage stage = new InterviewProcessStage();
        stage.setAiRecordingFileName("ai.webm");
        InterviewVideoSession session = new InterviewVideoSession();
        session.setRecordingFileName("merged.webm");
        session.setMergedRecordingFileName("merged.webm");
        session.setHrRecordingFileName("hr.webm");
        session.setIntervieweeRecordingFileName("candidate.webm");
        when(interviewProcessStageMapper.selectList(any())).thenReturn(List.of(stage));
        when(interviewVideoSessionMapper.selectList(any())).thenReturn(List.of(session));

        Object artifacts = ReflectionTestUtils.invokeMethod(service,
                "collectRecordingArtifacts", List.of(process));
        ReflectionTestUtils.invokeMethod(service, "deleteRecordingArtifacts", artifacts);

        verify(s3ObjectStorageService).deleteObjectsIfEnabled(Set.of(
                "interview-recordings/ai.webm",
                "interview-recordings/merged.webm",
                "interview-recordings/hr.webm",
                "interview-recordings/candidate.webm"));
    }
}
