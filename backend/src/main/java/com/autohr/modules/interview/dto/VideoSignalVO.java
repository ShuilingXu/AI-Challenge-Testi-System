package com.autohr.modules.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoSignalVO {
    private Long sessionId;
    private Long processId;
    private String videoSerialNo;
    private String videoJoinLink;
    private String offerSdp;
    private String answerSdp;
    private String hrIceCandidates;
    private String intervieweeIceCandidates;
    private String recordingPath;
    private String recordingFileName;
    private String sessionStatus;
    private LocalDateTime recordingEndRequestedAt;
    private String transcriptText;
    private String summaryText;
    private String summaryStatus;
}
