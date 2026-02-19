package top.cacl2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class BackupConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("cacl2backup");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cacl2backup.json");

    private int backupIntervalMinutes = 30;
    private int maxBackups = 10;
    private int compressionThreads = Runtime.getRuntime().availableProcessors();
    private int compressionLevel = 6;
    private boolean autoBackupEnabled = true;
    private boolean autoCleanupEnabled = true;
    private int maxBackupAgeDays = 7;
    private String backupFolderName = "backups";
    private boolean broadcastBackupMessages = true;
    private boolean saveOnBackup = true;

    public static BackupConfig load() {
        BackupConfig config = new BackupConfig();
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                BackupConfig loaded = GSON.fromJson(json, BackupConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public long getBackupIntervalMillis() {
        return TimeUnit.MINUTES.toMillis(backupIntervalMinutes);
    }

    public int getBackupIntervalMinutes() {
        return backupIntervalMinutes;
    }

    public void setBackupIntervalMinutes(int backupIntervalMinutes) {
        this.backupIntervalMinutes = Math.max(1, backupIntervalMinutes);
    }

    public int getMaxBackups() {
        return maxBackups;
    }

    public void setMaxBackups(int maxBackups) {
        this.maxBackups = Math.max(1, maxBackups);
    }

    public int getCompressionThreads() {
        return compressionThreads;
    }

    public void setCompressionThreads(int compressionThreads) {
        this.compressionThreads = Math.max(1, compressionThreads);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = Math.max(1, Math.min(9, compressionLevel));
    }

    public boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }

    public void setAutoBackupEnabled(boolean autoBackupEnabled) {
        this.autoBackupEnabled = autoBackupEnabled;
    }

    public boolean isAutoCleanupEnabled() {
        return autoCleanupEnabled;
    }

    public void setAutoCleanupEnabled(boolean autoCleanupEnabled) {
        this.autoCleanupEnabled = autoCleanupEnabled;
    }

    public int getMaxBackupAgeDays() {
        return maxBackupAgeDays;
    }

    public void setMaxBackupAgeDays(int maxBackupAgeDays) {
        this.maxBackupAgeDays = Math.max(1, maxBackupAgeDays);
    }

    public String getBackupFolderName() {
        return backupFolderName;
    }

    public void setBackupFolderName(String backupFolderName) {
        this.backupFolderName = backupFolderName;
    }

    public boolean isBroadcastBackupMessages() {
        return broadcastBackupMessages;
    }

    public void setBroadcastBackupMessages(boolean broadcastBackupMessages) {
        this.broadcastBackupMessages = broadcastBackupMessages;
    }

    public boolean isSaveOnBackup() {
        return saveOnBackup;
    }

    public void setSaveOnBackup(boolean saveOnBackup) {
        this.saveOnBackup = saveOnBackup;
    }
}
