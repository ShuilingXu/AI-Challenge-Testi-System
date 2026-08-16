package com.autohr.common.file;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class UploadPaths {

    public static final Path RESUME_DIR = Paths.get(System.getProperty("user.dir"), "uploads", "resumes").toAbsolutePath().normalize();
    public static final Path RECORDING_DIR = Paths.get(System.getProperty("user.dir"), "uploads", "interview-recordings").toAbsolutePath().normalize();

    private UploadPaths() {
    }
}
