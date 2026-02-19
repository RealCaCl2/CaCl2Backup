package top.cacl2.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import top.cacl2.backup.*;
import top.cacl2.config.BackupConfig;

import java.nio.file.Path;
import java.util.List;

public class BackupCommand {
    private final BackupManager backupManager;
    private final RestoreManager restoreManager;
    private final BackupScheduler scheduler;
    private final BackupConfig config;

    public BackupCommand(BackupManager backupManager, RestoreManager restoreManager, 
                         BackupScheduler scheduler, BackupConfig config) {
        this.backupManager = backupManager;
        this.restoreManager = restoreManager;
        this.scheduler = scheduler;
        this.config = config;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backup")
            .requires(source -> {
                if (!source.isPlayer()) return true;
                com.mojang.authlib.GameProfile profile = source.getPlayer().getGameProfile();
                return source.getServer().getPlayerList().isOp(new net.minecraft.server.players.NameAndId(profile.id(), profile.name()));
            })
            .then(Commands.literal("create")
                .executes(this::createBackup)
                .then(Commands.argument("label", StringArgumentType.greedyString())
                    .executes(this::createBackupWithLabel)))
            .then(Commands.literal("list")
                .executes(this::listBackups))
            .then(Commands.literal("restore")
                .then(Commands.argument("backup", StringArgumentType.greedyString())
                    .executes(this::restoreBackup)))
            .then(Commands.literal("delete")
                .then(Commands.argument("backup", StringArgumentType.greedyString())
                    .executes(this::deleteBackup)))
            .then(Commands.literal("cleanup")
                .executes(this::cleanupBackups))
            .then(Commands.literal("status")
                .executes(this::showStatus))
            .then(Commands.literal("reload")
                .executes(this::reloadConfig))
            .then(Commands.literal("config")
                .executes(this::showConfig)
                .then(Commands.literal("interval")
                    .executes(this::showInterval)
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(this::setInterval)))
                .then(Commands.literal("maxbackups")
                    .executes(this::showMaxBackups)
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(this::setMaxBackups)))
                .then(Commands.literal("maxage")
                    .executes(this::showMaxAge)
                    .then(Commands.argument("days", IntegerArgumentType.integer(1))
                        .executes(this::setMaxAge)))
                .then(Commands.literal("autobackup")
                    .executes(this::showAutoBackup)
                    .then(Commands.argument("enabled", StringArgumentType.word())
                        .executes(this::setAutoBackup)))
                .then(Commands.literal("autocleanup")
                    .executes(this::showAutoCleanup)
                    .then(Commands.argument("enabled", StringArgumentType.word())
                        .executes(this::setAutoCleanup)))
                .then(Commands.literal("threads")
                    .executes(this::showThreads)
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(this::setThreads)))
                .then(Commands.literal("level")
                    .executes(this::showLevel)
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 9))
                        .executes(this::setCompressionLevel)))));
    }

    private int createBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (backupManager.isBackingUp()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] A backup is already in progress!"));
            return 0;
        }

        if (config.isSaveOnBackup()) {
            saveWorld(source.getServer());
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Starting backup..."), true);
        
        backupManager.createBackup().thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()), true);
                if (config.isAutoCleanupEnabled()) {
                    doCleanup(source);
                }
            } else {
                source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()));
            }
        });
        
        return 1;
    }

    private int createBackupWithLabel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String label = StringArgumentType.getString(context, "label");
        
        if (backupManager.isBackingUp()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] A backup is already in progress!"));
            return 0;
        }

        if (config.isSaveOnBackup()) {
            saveWorld(source.getServer());
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Starting backup with label: " + label), true);
        
        backupManager.createBackup(label).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()), true);
                if (config.isAutoCleanupEnabled()) {
                    doCleanup(source);
                }
            } else {
                source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()));
            }
        });
        
        return 1;
    }

    private void doCleanup(CommandSourceStack source) {
        BackupCleaner cleaner = new BackupCleaner(
            backupManager.getBackupDir(),
            config.getMaxBackups(),
            config.getMaxBackupAgeDays()
        );
        int deleted = cleaner.cleanup();
        if (deleted > 0) {
            final int count = deleted;
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Auto cleaned up " + count + " old backup(s)"), false);
        }
    }

    private void saveWorld(MinecraftServer server) {
        try {
            server.saveEverything(true, true, true);
            server.getPlayerList().saveAll();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cacl2backup").warn("Failed to save world before backup", e);
        }
    }

    private int listBackups(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<BackupManager.BackupInfo> backups = backupManager.listBackups();
        
        if (backups.isEmpty()) {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] No backups found."), false);
            return 0;
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Available backups (" + backups.size() + "):"), false);
        
        for (int i = 0; i < Math.min(backups.size(), 20); i++) {
            BackupManager.BackupInfo info = backups.get(i);
            String label = info.getLabel().isEmpty() ? "" : " [" + info.getLabel() + "]";
            final int index = i;
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                String.format("%d. %s%s - %s", 
                    index + 1, 
                    info.getFormattedTime(), 
                    label,
                    info.getFormattedSize())), false);
        }
        
        if (backups.size() > 20) {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("... and " + (backups.size() - 20) + " more"), false);
        }
        
        return backups.size();
    }

    private int restoreBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "backup");
        
        if (restoreManager.isRestoring()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] A restore is already in progress!"));
            return 0;
        }

        String backupName = resolveBackupName(input);
        if (backupName == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] Invalid backup number or name: " + input));
            return 0;
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] WARNING: This will replace the current world!"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Preparing restore from: " + backupName), true);
        
        restoreManager.restoreBackup(backupName).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()), true);
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] STOP the server now to complete the restore!"), true);
            } else {
                source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] " + result.getMessage()));
            }
        });
        
        return 1;
    }

    private int deleteBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "backup");
        
        String backupName = resolveBackupName(input);
        if (backupName == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] Invalid backup number or name: " + input));
            return 0;
        }
        
        Path backupFile = backupManager.getBackupDir().resolve(backupName);
        if (!backupName.endsWith(".zip")) {
            backupFile = backupManager.getBackupDir().resolve(backupName + ".zip");
        }
        
        if (backupManager.deleteBackup(backupFile)) {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Deleted backup: " + backupName), true);
            return 1;
        } else {
            source.sendFailure(net.minecraft.network.chat.Component.literal("[CaCl2Backup] Failed to delete backup: " + backupName));
            return 0;
        }
    }

    private String resolveBackupName(String input) {
        try {
            int index = Integer.parseInt(input);
            List<BackupManager.BackupInfo> backups = backupManager.listBackups();
            if (index < 1 || index > backups.size()) {
                return null;
            }
            return backups.get(index - 1).getFile().getFileName().toString();
        } catch (NumberFormatException e) {
            return input;
        }
    }

    private int cleanupBackups(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        BackupCleaner cleaner = new BackupCleaner(
            backupManager.getBackupDir(),
            config.getMaxBackups(),
            config.getMaxBackupAgeDays()
        );
        
        int deleted = cleaner.cleanup();
        final int count = deleted;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Cleaned up " + count + " old backup(s)"), true);
        
        return deleted;
    }

    private int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Status:"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Auto Backup: " + (config.isAutoBackupEnabled() ? "Enabled" : "Disabled")), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Auto Cleanup: " + (config.isAutoCleanupEnabled() ? "Enabled" : "Disabled")), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Backup Interval: " + config.getBackupIntervalMinutes() + " minutes"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Max Backups: " + config.getMaxBackups()), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Max Backup Age: " + config.getMaxBackupAgeDays() + " days"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Compression Threads: " + config.getCompressionThreads()), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Compression Level: " + config.getCompressionLevel()), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Currently Backing Up: " + (backupManager.isBackingUp() ? "Yes" : "No")), false);
        
        List<BackupManager.BackupInfo> backups = backupManager.listBackups();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" Total Backups: " + backups.size()), false);
        
        return 1;
    }

    private int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        BackupConfig newConfig = BackupConfig.load();
        config.setBackupIntervalMinutes(newConfig.getBackupIntervalMinutes());
        config.setMaxBackups(newConfig.getMaxBackups());
        config.setMaxBackupAgeDays(newConfig.getMaxBackupAgeDays());
        config.setAutoBackupEnabled(newConfig.isAutoBackupEnabled());
        config.setAutoCleanupEnabled(newConfig.isAutoCleanupEnabled());
        config.setCompressionThreads(newConfig.getCompressionThreads());
        config.setCompressionLevel(newConfig.getCompressionLevel());
        
        scheduler.restart();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Configuration reloaded!"), true);
        return 1;
    }

    private int setInterval(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        
        config.setBackupIntervalMinutes(minutes);
        config.save();
        scheduler.restart();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Backup interval set to " + minutes + " minutes"), true);
        return 1;
    }

    private int setMaxBackups(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = IntegerArgumentType.getInteger(context, "count");
        
        config.setMaxBackups(count);
        config.save();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Max backups set to " + count), true);
        return 1;
    }

    private int setMaxAge(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int days = IntegerArgumentType.getInteger(context, "days");
        
        config.setMaxBackupAgeDays(days);
        config.save();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Max backup age set to " + days + " days"), true);
        return 1;
    }

    private int setAutoBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String enabled = StringArgumentType.getString(context, "enabled").toLowerCase();
        
        boolean value = enabled.equals("true") || enabled.equals("on") || enabled.equals("1");
        config.setAutoBackupEnabled(value);
        config.save();
        scheduler.restart();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Auto backup " + (value ? "enabled" : "disabled")), true);
        return 1;
    }

    private int setAutoCleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String enabled = StringArgumentType.getString(context, "enabled").toLowerCase();
        
        boolean value = enabled.equals("true") || enabled.equals("on") || enabled.equals("1");
        config.setAutoCleanupEnabled(value);
        config.save();
        scheduler.restart();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Auto cleanup " + (value ? "enabled" : "disabled")), true);
        return 1;
    }

    private int setThreads(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = IntegerArgumentType.getInteger(context, "count");
        
        config.setCompressionThreads(count);
        config.save();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Compression threads set to " + count), true);
        return 1;
    }

    private int setCompressionLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int level = IntegerArgumentType.getInteger(context, "level");
        
        config.setCompressionLevel(level);
        config.save();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Compression level set to " + level), true);
        return 1;
    }

    private int showConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Configuration:"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" interval: " + config.getBackupIntervalMinutes() + " minutes"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" maxbackups: " + config.getMaxBackups()), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" maxage: " + config.getMaxBackupAgeDays() + " days"), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" autobackup: " + (config.isAutoBackupEnabled() ? "on" : "off")), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" autocleanup: " + (config.isAutoCleanupEnabled() ? "on" : "off")), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" threads: " + config.getCompressionThreads()), false);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(" level: " + config.getCompressionLevel()), false);
        
        return 1;
    }

    private int showInterval(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Current interval: " + config.getBackupIntervalMinutes() + " minutes"), false);
        return 1;
    }

    private int showMaxBackups(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Current max backups: " + config.getMaxBackups()), false);
        return 1;
    }

    private int showMaxAge(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Current max age: " + config.getMaxBackupAgeDays() + " days"), false);
        return 1;
    }

    private int showAutoBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Auto backup: " + (config.isAutoBackupEnabled() ? "on" : "off")), false);
        return 1;
    }

    private int showAutoCleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Auto cleanup: " + (config.isAutoCleanupEnabled() ? "on" : "off")), false);
        return 1;
    }

    private int showThreads(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Current threads: " + config.getCompressionThreads()), false);
        return 1;
    }

    private int showLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[CaCl2Backup] Current compression level: " + config.getCompressionLevel()), false);
        return 1;
    }
}
