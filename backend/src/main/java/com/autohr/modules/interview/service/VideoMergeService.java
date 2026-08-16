package com.autohr.modules.interview.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.interview.entity.InterviewVideoSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class VideoMergeService {

    private final S3ObjectStorageService s3ObjectStorageService;

    @Value("${interview.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${interview.video.video-codec:libvpx-vp9}")
    private String videoCodec;

    @Value("${interview.video.audio-codec:libopus}")
    private String audioCodec;

    public boolean canMerge(InterviewVideoSession session) {
        return isReadableRecording(session.getHrRecordingPath()) && isReadableRecording(session.getIntervieweeRecordingPath());
    }

    public void mergeRecordings(InterviewVideoSession session) {
        if (!canMerge(session)) {
            return;
        }
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            Path output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-merged.webm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("拼接视频路径非法");
            }
            mergeSideBySide(session, output);
            s3ObjectStorageService.archiveIfEnabled(output, "interview-recordings/" + output.getFileName(), "video/webm");
            session.setMergedRecordingPath(output.toString());
            session.setMergedRecordingFileName(output.getFileName().toString());
            session.setRecordingPath(output.toString());
            session.setRecordingFileName(output.getFileName().toString());
        } catch (IOException ex) {
            throw new BusinessException("拼接视频失败: " + ex.getMessage());
        }
    }

    public void extractAudio(InterviewVideoSession session) {
        String source = session.getMergedRecordingPath() == null ? session.getRecordingPath() : session.getMergedRecordingPath();
        if (source == null || !Files.isRegularFile(Path.of(source))) {
            throw new BusinessException("视频文件不存在，不能分离音频");
        }
        Path output = null;
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-audio.pcm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("音频文件路径非法");
            }
            if (canMerge(session)) {
                runFfmpeg(List.of(
                        ffmpegPath,
                        "-y",
                        "-i", session.getHrRecordingPath(),
                        "-i", session.getIntervieweeRecordingPath(),
                        "-filter_complex", "[0:a:0][1:a:0]amix=inputs=2:duration=longest:dropout_transition=0,aresample=16000[a]",
                        "-map", "[a]",
                        "-ac", "1",
                        "-ar", "16000",
                        "-f", "s16le",
                        "-c:a", "pcm_s16le",
                        output.toString()
                ), "混合双方音频失败");
            } else {
                runFfmpeg(List.of(
                        ffmpegPath,
                        "-y",
                        "-i", source,
                        "-map", "0:a:0",
                        "-vn",
                        "-ac", "1",
                        "-ar", "16000",
                        "-f", "s16le",
                        "-c:a", "pcm_s16le",
                        output.toString()
                ), "分离音频失败");
            }
            session.setAudioPath(output.toString());
            session.setAudioFileName(output.getFileName().toString());
        } catch (IOException ex) {
            throw new BusinessException("分离音频失败: " + ex.getMessage());
        } catch (RuntimeException ex) {
            deleteTemporaryAudio(output == null ? null : output.toString());
            throw ex;
        }
    }

    public String extractSpeakerAudio(InterviewVideoSession session, String speaker) {
        String source = "hr".equals(speaker) ? session.getHrRecordingPath() : session.getIntervieweeRecordingPath();
        if (source == null || !Files.isRegularFile(Path.of(source))) {
            throw new BusinessException("" + speaker + "录制文件不存在，不能分离音频");
        }
        Path output = null;
        try {
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            output = UploadPaths.RECORDING_DIR.resolve(session.getVideoSerialNo() + "-" + speaker + "-audio.pcm").normalize().toAbsolutePath();
            if (!output.startsWith(UploadPaths.RECORDING_DIR)) {
                throw new BusinessException("说话人音频文件路径非法");
            }
            runFfmpeg(List.of(
                    ffmpegPath,
                    "-y",
                    "-i", source,
                    "-map", "0:a:0",
                    "-vn",
                    "-ac", "1",
                    "-ar", "16000",
                    "-f", "s16le",
                    "-c:a", "pcm_s16le",
                    output.toString()
            ), "分离" + speaker + "音频失败");
            return output.toString();
        } catch (IOException ex) {
            throw new BusinessException("分离" + speaker + "音频失败: " + ex.getMessage());
        } catch (RuntimeException ex) {
            deleteTemporaryAudio(output == null ? null : output.toString());
            throw ex;
        }
    }

    private void mergeSideBySide(InterviewVideoSession session, Path output) {
        runFfmpeg(List.of(
                ffmpegPath,
                "-y",
                "-i", session.getHrRecordingPath(),
                "-i", session.getIntervieweeRecordingPath(),
                "-filter_complex", "[0:v]scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,setsar=1[v0];[1:v]scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,setsar=1[v1];[v0][v1]hstack=inputs=2[v];[0:a:0][1:a:0]amix=inputs=2:duration=longest:dropout_transition=0[a]",
                "-map", "[v]",
                "-map", "[a]",
                "-c:v", videoCodec,
                "-b:v", "1600k",
                "-c:a", audioCodec,
                output.toString()
        ), "横向拼接视频失败");
    }

    private void runFfmpeg(List<String> command, String errorMessage) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            AtomicReference<String> output = new AtomicReference<>("");
            AtomicReference<IOException> outputFailure = new AtomicReference<>();
            Thread outputReader = new Thread(() -> {
                try {
                    output.set(readProcessOutput(process.getInputStream()));
                } catch (IOException ex) {
                    outputFailure.set(ex);
                }
            }, "ffmpeg-output-reader");
            outputReader.setDaemon(true);
            outputReader.start();
            boolean exited = process.waitFor(5, TimeUnit.MINUTES);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                outputReader.interrupt();
                throw new BusinessException(errorMessage + ": ffmpeg执行超时");
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(10));
            if (outputReader.isAlive()) {
                outputReader.interrupt();
                throw new BusinessException(errorMessage + ": unable to read ffmpeg output");
            }
            if (outputFailure.get() != null) {
                throw new BusinessException(errorMessage + ": unable to read ffmpeg output");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(errorMessage + ": " + abbreviate(output.get()));
            }
        } catch (IOException ex) {
            throw new BusinessException(errorMessage + ": 未找到ffmpeg，请安装ffmpeg或配置INTERVIEW_VIDEO_FFMPEG_PATH/FFMPEG_PATH");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(errorMessage + ": ffmpeg执行被中断");
        }
    }

    private String readProcessOutput(InputStream inputStream) throws IOException {
        final int maxOutputBytes = 8192;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            int remaining = maxOutputBytes - output.size();
            if (remaining > 0) {
                output.write(buffer, 0, Math.min(read, remaining));
            }
        }
        return output.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private boolean isReadableRecording(String path) {
        return path != null && Files.isRegularFile(Path.of(path)) && Files.isReadable(Path.of(path));
    }

    public void deleteTemporaryAudio(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(audioPath).normalize().toAbsolutePath();
            if (path.startsWith(UploadPaths.RECORDING_DIR)
                    && path.getFileName().toString().toLowerCase().endsWith(".pcm")) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // Temporary audio cleanup is best effort and must not replace the transcription result.
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "空输出";
        }
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }
}
