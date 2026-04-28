package top.cacl2.backup;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import top.cacl2.config.BackupConfig;

public class RestoreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cacl2backup");
    private final BackupManager backupManager;
    private final Path gameDir;
    private final Path worldDir;
    private final Path pendingRestoreFile;
    private final ScheduledExecutorService scheduler;
    private volatile boolean isRestoring = false;

    public RestoreManager(BackupManager backupManager, Path gameDir) {
        this.backupManager = backupManager;
        this.gameDir = gameDir;
        this.worldDir = gameDir.resolve("world");
        this.pendingRestoreFile = gameDir.resolve(".pending_restore");
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "cacl2backup-restart-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<RestoreResult> restoreBackup(MinecraftServer server, BackupConfig config, String backupName) {
        if (isRestoring) {
            return CompletableFuture.completedFuture(
                new RestoreResult(false, "A restore is already in progress")
            );
        }

        Path backupFile = backupManager.getBackupDir().resolve(backupName);
        if (!backupName.endsWith(".zip")) {
            backupFile = backupManager.getBackupDir().resolve(backupName + ".zip");
        }

        if (!Files.exists(backupFile)) {
            return CompletableFuture.completedFuture(
                new RestoreResult(false, "Backup file not found: " + backupFile)
            );
        }

        final Path finalBackupFile = backupFile;
        final boolean autoRestart = config.isAutoRestartAfterRestore();
        final int delaySeconds = config.getRestartDelaySeconds();
        final boolean broadcastMsg = config.isBroadcastRestoreMessage();
        final String msgTemplate = config.getRestoreRestartMessage();

        return CompletableFuture.supplyAsync(() -> {
            isRestoring = true;
            long startTime = System.currentTimeMillis();

            try {
                Path tempWorldDir = worldDir.resolveSibling("world_temp_restore");

                deleteDirectory(tempWorldDir);
                Files.createDirectories(tempWorldDir);

                LOGGER.info("Starting restore from: {}", finalBackupFile);
                decompressArchive(finalBackupFile, tempWorldDir);

                savePendingRestore(tempWorldDir, finalBackupFile.getFileName().toString());

                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("Restore prepared in {}ms", duration);

                if (autoRestart) {
                    String message = msgTemplate.replace("{seconds}", String.valueOf(delaySeconds));
                    if (broadcastMsg) {
                        net.minecraft.network.chat.Component component = net.minecraft.network.chat.Component.literal(
                            "[CaCl2Backup] §e" + message
                        );
                        server.getPlayerList().getPlayers().forEach(player ->
                            player.sendSystemMessage(component)
                        );
                    }

                    final MinecraftServer finalServer = server;
                    scheduler.schedule(() -> {
                        LOGGER.info("Initiating server restart for restore completion");
                        finalServer.halt(true);
                    }, delaySeconds, TimeUnit.SECONDS);

                    return new RestoreResult(true,
                        "Backup extracted! Server will restart in " + delaySeconds + " seconds to complete the restore.");
                } else {
                    return new RestoreResult(true,
                        "Backup extracted successfully! Please STOP the server to complete the restore. " +
                        "The world will be replaced when the server stops.");
                }

            } catch (Exception e) {
                LOGGER.error("Restore failed", e);
                return new RestoreResult(false, "Restore failed: " + e.getMessage());
            } finally {
                isRestoring = false;
            }
        });
    }

    private void savePendingRestore(Path tempWorldDir, String backupName) throws IOException {
        Properties props = new Properties();
        props.setProperty("tempWorldPath", tempWorldDir.toString());
        props.setProperty("backupName", backupName);
        props.setProperty("timestamp", String.valueOf(System.currentTimeMillis()));

        try (OutputStream os = Files.newOutputStream(pendingRestoreFile)) {
            props.store(os, "Pending restore - do not delete this file");
        }
        LOGGER.info("Pending restore saved. Server must be stopped to complete restore.");
    }

    public boolean hasPendingRestore() {
        return Files.exists(pendingRestoreFile);
    }

    public void executePendingRestore() {
        if (!hasPendingRestore()) {
            return;
        }

        LOGGER.info("Executing pending restore...");

        try {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(pendingRestoreFile)) {
                props.load(is);
            }

            Path tempWorldDir = Paths.get(props.getProperty("tempWorldPath"));
            String backupName = props.getProperty("backupName");

            if (!Files.exists(tempWorldDir)) {
                LOGGER.error("Temp world directory not found: {}", tempWorldDir);
                Files.deleteIfExists(pendingRestoreFile);
                return;
            }

            LOGGER.info("Removing current world...");
            deleteDirectory(worldDir);

            LOGGER.info("Moving restored world from: {}", tempWorldDir);
            Files.move(tempWorldDir, worldDir, StandardCopyOption.REPLACE_EXISTING);

            Files.deleteIfExists(pendingRestoreFile);

            LOGGER.info("Restore completed successfully from: {}", backupName);

        } catch (Exception e) {
            LOGGER.error("Failed to execute pending restore", e);
        }
    }

    private void decompressArchive(Path zipFile, Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                Path targetPath = targetDir.resolve(entryName);

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        LOGGER.info("Decompression completed to: {}", targetDir);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public boolean isRestoring() {
        return isRestoring;
    }

    public Path getWorldDir() {
        return worldDir;
    }

    public static class RestoreResult {
        private final boolean success;
        private final String message;

        public RestoreResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
