package top.cacl2.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BackupManager {
    private static final DateTimeFormatter BACKUP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final Path backupDir;
    private final Path worldDir;
    private final CompressionUtil compressionUtil;
    private volatile boolean isBackingUp = false;

    public BackupManager(Path gameDir, String backupFolderName, int compressionLevel, int threads) {
        this.backupDir = gameDir.resolve(backupFolderName);
        this.worldDir = gameDir.resolve("world");
        this.compressionUtil = new CompressionUtil(compressionLevel, threads);
        
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create backup directory", e);
        }
    }

    public CompletableFuture<BackupResult> createBackup(String label) {
        if (isBackingUp) {
            return CompletableFuture.completedFuture(
                new BackupResult(null, false, "A backup is already in progress", 0)
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            isBackingUp = true;
            long startTime = System.currentTimeMillis();
            
            try {
                if (!Files.exists(worldDir)) {
                    return new BackupResult(null, false, "World directory not found", 0);
                }

                String timestamp = LocalDateTime.now().format(BACKUP_FORMATTER);
                String backupName = label != null && !label.isEmpty() 
                    ? String.format("backup_%s_%s.zip", timestamp, sanitizeLabel(label))
                    : String.format("backup_%s.zip", timestamp);
                
                Path backupFile = backupDir.resolve(backupName);
                
                CompressionUtil.CompressionResult result = compressionUtil.compressDirectory(worldDir, backupFile);
                
                long duration = System.currentTimeMillis() - startTime;
                
                return new BackupResult(
                    backupFile,
                    true,
                    String.format("Backup created: %s (%s -> %s, ratio: %.1f%%, time: %dms, threads: %d)",
                        backupName,
                        result.getFormattedSize(result.getOriginalSize()),
                        result.getFormattedSize(result.getCompressedSize()),
                        (1 - result.getCompressionRatio()) * 100,
                        duration,
                        result.getThreadsUsed()
                    ),
                    duration
                );
            } catch (Exception e) {
                return new BackupResult(null, false, "Backup failed: " + e.getMessage(), 
                    System.currentTimeMillis() - startTime);
            } finally {
                isBackingUp = false;
            }
        });
    }

    public BackupResult createBackupSync(String label) {
        if (isBackingUp) {
            return new BackupResult(null, false, "A backup is already in progress", 0);
        }
        
        isBackingUp = true;
        long startTime = System.currentTimeMillis();
        
        try {
            if (!Files.exists(worldDir)) {
                return new BackupResult(null, false, "World directory not found", 0);
            }

            String timestamp = LocalDateTime.now().format(BACKUP_FORMATTER);
            String backupName = label != null && !label.isEmpty() 
                ? String.format("backup_%s_%s.zip", timestamp, sanitizeLabel(label))
                : String.format("backup_%s.zip", timestamp);
            
            Path backupFile = backupDir.resolve(backupName);
            
            CompressionUtil.CompressionResult result = compressionUtil.compressDirectory(worldDir, backupFile);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return new BackupResult(
                backupFile,
                true,
                String.format("Backup created: %s (%s -> %s, ratio: %.1f%%, time: %dms, threads: %d)",
                    backupName,
                    result.getFormattedSize(result.getOriginalSize()),
                    result.getFormattedSize(result.getCompressedSize()),
                    (1 - result.getCompressionRatio()) * 100,
                    duration,
                    result.getThreadsUsed()
                ),
                duration
            );
        } catch (Exception e) {
            return new BackupResult(null, false, "Backup failed: " + e.getMessage(), 
                System.currentTimeMillis() - startTime);
        } finally {
            isBackingUp = false;
        }
    }

    public CompletableFuture<BackupResult> createBackup() {
        return createBackup(null);
    }

    public List<BackupInfo> listBackups() {
        try {
            if (!Files.exists(backupDir)) {
                return Collections.emptyList();
            }
            
            return Files.list(backupDir)
                .filter(p -> p.toString().endsWith(".zip"))
                .map(this::getBackupInfo)
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getCreationTime().compareTo(a.getCreationTime()))
                .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public BackupInfo getBackupInfo(Path backupFile) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(backupFile, BasicFileAttributes.class);
            FileTime creationTime = attrs.creationTime();
            long size = attrs.size();
            String filename = backupFile.getFileName().toString();
            
            String label = "";
            if (filename.contains("_") && filename.endsWith(".zip")) {
                String[] parts = filename.replace("backup_", "").replace(".zip", "").split("_");
                if (parts.length > 2) {
                    label = String.join("_", Arrays.copyOfRange(parts, 2, parts.length));
                }
            }
            
            return new BackupInfo(
                backupFile,
                LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault()),
                size,
                label
            );
        } catch (IOException e) {
            return null;
        }
    }

    public boolean deleteBackup(Path backupFile) {
        try {
            return Files.deleteIfExists(backupFile);
        } catch (IOException e) {
            return false;
        }
    }

    public void shutdown() {
        compressionUtil.shutdown();
    }

    public boolean isBackingUp() {
        return isBackingUp;
    }

    public Path getBackupDir() {
        return backupDir;
    }

    private String sanitizeLabel(String label) {
        return label.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public static class BackupResult {
        private final Path backupFile;
        private final boolean success;
        private final String message;
        private final long durationMs;

        public BackupResult(Path backupFile, boolean success, String message, long durationMs) {
            this.backupFile = backupFile;
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
        }

        public Path getBackupFile() { return backupFile; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }
    }

    public static class BackupInfo {
        private final Path file;
        private final LocalDateTime creationTime;
        private final long size;
        private final String label;

        public BackupInfo(Path file, LocalDateTime creationTime, long size, String label) {
            this.file = file;
            this.creationTime = creationTime;
            this.size = size;
            this.label = label;
        }

        public Path getFile() { return file; }
        public LocalDateTime getCreationTime() { return creationTime; }
        public long getSize() { return size; }
        public String getLabel() { return label; }
        
        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
        
        public String getFormattedTime() {
            return creationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
