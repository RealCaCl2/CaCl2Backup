package top.cacl2.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class BackupCleaner {
    private final Path backupDir;
    private final int maxBackups;
    private final int maxAgeDays;

    public BackupCleaner(Path backupDir, int maxBackups, int maxAgeDays) {
        this.backupDir = backupDir;
        this.maxBackups = maxBackups;
        this.maxAgeDays = maxAgeDays;
    }

    public int cleanup() {
        if (!Files.exists(backupDir)) {
            return 0;
        }

        List<BackupInfo> backups;
        try {
            backups = Files.list(backupDir)
                .filter(p -> p.toString().endsWith(".zip"))
                .map(this::toBackupInfo)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BackupInfo::getCreationTime).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            return 0;
        }

        int deletedCount = 0;
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(maxAgeDays);
        
        Set<Path> toDelete = new HashSet<>();
        
        int index = 0;
        for (BackupInfo backup : backups) {
            index++;
            if (backup.getCreationTime().isBefore(cutoffDate)) {
                toDelete.add(backup.getFile());
            } else if (index > maxBackups) {
                toDelete.add(backup.getFile());
            }
        }

        for (Path file : toDelete) {
            try {
                Files.deleteIfExists(file);
                deletedCount++;
            } catch (IOException e) {
                // Ignore deletion failures
            }
        }

        return deletedCount;
    }

    private BackupInfo toBackupInfo(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            FileTime creationTime = attrs.creationTime();
            long size = attrs.size();
            
            return new BackupInfo(
                file,
                LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault()),
                size
            );
        } catch (IOException e) {
            return null;
        }
    }

    public List<BackupInfo> getBackupsToDelete() {
        if (!Files.exists(backupDir)) {
            return Collections.emptyList();
        }

        try {
            List<BackupInfo> backups = Files.list(backupDir)
                .filter(p -> p.toString().endsWith(".zip"))
                .map(this::toBackupInfo)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BackupInfo::getCreationTime).reversed())
                .collect(Collectors.toList());

            List<BackupInfo> toDelete = new ArrayList<>();
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(maxAgeDays);
            
            int index = 0;
            for (BackupInfo backup : backups) {
                index++;
                if (backup.getCreationTime().isBefore(cutoffDate) || index > maxBackups) {
                    toDelete.add(backup);
                }
            }
            
            return toDelete;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static class BackupInfo {
        private final Path file;
        private final LocalDateTime creationTime;
        private final long size;

        public BackupInfo(Path file, LocalDateTime creationTime, long size) {
            this.file = file;
            this.creationTime = creationTime;
            this.size = size;
        }

        public Path getFile() { return file; }
        public LocalDateTime getCreationTime() { return creationTime; }
        public long getSize() { return size; }
    }
}
