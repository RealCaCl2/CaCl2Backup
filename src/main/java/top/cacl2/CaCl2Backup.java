package top.cacl2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.cacl2.backup.*;
import top.cacl2.command.BackupCommand;
import top.cacl2.config.BackupConfig;

import java.nio.file.Path;

public class CaCl2Backup implements ModInitializer {
    public static final String MOD_ID = "cacl2backup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BackupConfig config;
    private static BackupManager backupManager;
    private static RestoreManager restoreManager;
    private static BackupScheduler scheduler;
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("CaCl2Backup Mod initializing...");

        config = BackupConfig.load();
        Path gameDir = FabricLoader.getInstance().getGameDir();

        backupManager = new BackupManager(
            gameDir,
            config.getBackupFolderName(),
            config.getCompressionLevel(),
            config.getCompressionThreads()
        );

        restoreManager = new RestoreManager(backupManager, gameDir);

        scheduler = new BackupScheduler(backupManager, config, new BackupScheduler.BackupListener() {
            @Override
            public boolean onBackupStart(String label) {
                if (config.isSaveOnBackup() && server != null) {
                    try {
                        LOGGER.info("Saving world before backup...");
                        final boolean[] success = {false};
                        server.executeBlocking(() -> {
                            try {
                                server.saveEverything(true, true, true);
                                server.getPlayerList().saveAll();
                                LOGGER.info("World saved successfully");
                                success[0] = true;
                            } catch (Exception e) {
                                LOGGER.error("Failed to save before backup", e);
                            }
                        });
                        if (!success[0]) {
                            return false;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute save on main thread", e);
                        return false;
                    }
                }
                LOGGER.info("Backup started: {}", label);
                return true;
            }

            @Override
            public void onBackupComplete(BackupManager.BackupResult result) {
                LOGGER.info("Backup completed: {}", result.getMessage());
            }

            @Override
            public void onBackupFailed(String error) {
                LOGGER.error("Backup failed: {}", error);
            }

            @Override
            public void onCleanupComplete(int deletedCount) {
                LOGGER.info("Cleaned up {} old backup(s)", deletedCount);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            new BackupCommand(backupManager, restoreManager, scheduler, config).register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CaCl2Backup.server = server;
            
            if (restoreManager.hasPendingRestore()) {
                LOGGER.info("Pending restore detected! Executing restore before server starts...");
                restoreManager.executePendingRestore();
            }
            
            LOGGER.info("Server starting, backup system ready");
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            scheduler.start();
            LOGGER.info("Backup scheduler started (interval: {} minutes, auto: {})", 
                config.getBackupIntervalMinutes(), 
                config.isAutoBackupEnabled());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, shutting down backup system...");
            
            scheduler.stop();
            backupManager.shutdown();
            scheduler.shutdown();
            LOGGER.info("Backup system shutdown complete");
        });

        LOGGER.info("CaCl2Backup Mod initialized successfully!");
        LOGGER.info("Backup directory: {}", backupManager.getBackupDir());
        LOGGER.info("Auto backup: {}, Interval: {} minutes", 
            config.isAutoBackupEnabled(), config.getBackupIntervalMinutes());
    }

    public static BackupConfig getConfig() {
        return config;
    }

    public static BackupManager getBackupManager() {
        return backupManager;
    }

    public static RestoreManager getRestoreManager() {
        return restoreManager;
    }

    public static BackupScheduler getScheduler() {
        return scheduler;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
