package top.cacl2.backup;

import top.cacl2.config.BackupConfig;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class BackupScheduler {
    private final ScheduledExecutorService scheduler;
    private final BackupManager backupManager;
    private final BackupConfig config;
    private final BackupListener listener;
    private ScheduledFuture<?> backupTask;
    private ScheduledFuture<?> cleanupTask;
    private long nextBackupTime;

    public BackupScheduler(BackupManager backupManager, BackupConfig config, BackupListener listener) {
        this.backupManager = backupManager;
        this.config = config;
        this.listener = listener;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        if (config.isAutoBackupEnabled()) {
            startAutoBackup();
        }
        if (config.isAutoCleanupEnabled()) {
            startAutoCleanup();
        }
    }

    private void startAutoBackup() {
        if (backupTask != null) {
            backupTask.cancel(false);
        }
        
        nextBackupTime = System.currentTimeMillis() + config.getBackupIntervalMillis();
        backupTask = scheduler.scheduleAtFixedRate(() -> {
            nextBackupTime = System.currentTimeMillis() + config.getBackupIntervalMillis();
            if (backupManager.isBackingUp()) {
                return;
            }
            
            if (!listener.onBackupStart("auto")) {
                listener.onBackupFailed("World save failed, backup cancelled");
                return;
            }
            backupManager.createBackup("auto")
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        listener.onBackupComplete(result);
                        if (config.isAutoCleanupEnabled()) {
                            doCleanup();
                        }
                    } else {
                        listener.onBackupFailed(result.getMessage());
                    }
                });
        }, config.getBackupIntervalMillis(), config.getBackupIntervalMillis(), TimeUnit.MILLISECONDS);
    }

    private void doCleanup() {
        BackupCleaner cleaner = new BackupCleaner(
            backupManager.getBackupDir(),
            config.getMaxBackups(),
            config.getMaxBackupAgeDays()
        );
        int deleted = cleaner.cleanup();
        if (deleted > 0) {
            listener.onCleanupComplete(deleted);
        }
    }

    private void startAutoCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        
        cleanupTask = scheduler.scheduleAtFixedRate(() -> {
            BackupCleaner cleaner = new BackupCleaner(
                backupManager.getBackupDir(),
                config.getMaxBackups(),
                config.getMaxBackupAgeDays()
            );
            int deleted = cleaner.cleanup();
            if (deleted > 0) {
                listener.onCleanupComplete(deleted);
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    public void stop() {
        if (backupTask != null) {
            backupTask.cancel(false);
            backupTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void restart() {
        stop();
        start();
    }

    public boolean isRunning() {
        return backupTask != null && !backupTask.isCancelled();
    }

    public String getNextBackupTimeFormatted() {
        if (!isRunning()) {
            return "N/A (Auto backup disabled)";
        }
        
        long remaining = nextBackupTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return "Soon";
        }
        
        long minutes = remaining / 60000;
        long seconds = (remaining % 60000) / 1000;
        
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(nextBackupTime), 
            ZoneId.systemDefault()
        );
        String timeStr = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        return String.format("%s (in %dm %ds)", timeStr, minutes, seconds);
    }

    public interface BackupListener {
        boolean onBackupStart(String label);
        void onBackupComplete(BackupManager.BackupResult result);
        void onBackupFailed(String error);
        void onCleanupComplete(int deletedCount);
    }
}
