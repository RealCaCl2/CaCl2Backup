# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build (requires Java 25)
JAVA_HOME="C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot" ./gradlew build

# Run in test server
cd test-server && java -jar fabric-server-launch.jar
```

## Project Overview

**CaCl2Backup** is a Minecraft server backup mod for Fabric. It provides automatic scheduled backups, multi-threaded compression, automatic cleanup of old backups, and world restore functionality.

- **Minecraft**: 26.1.2 (non-obfuscated environment)
- **Java**: 25+
- **Plugin**: `net.fabricmc.fabric-loom` (NOT `fabric-loom-remap` - this is a non-obfuscated mod)
- **Note**: MC 26.x uses non-obfuscated mappings. Do NOT use `mappings loom.officialMojangMappings()` or `loom.layered {}` blocks. Dependencies use `implementation`, not `modImplementation`.

## Architecture

```
CaCl2Backup.java          # Mod entry, wires together all components
├── BackupConfig.java     # JSON config at config/cacl2backup.json
├── BackupManager.java    # Compression and backup file management
│   └── CompressionUtil.java  # Multi-threaded ZIP compression
├── BackupScheduler.java  # ScheduledExecutorService for auto-backup
├── BackupCleaner.java    # Cleanup old backups by count/age
├── RestoreManager.java   # World restore with pending-restore mechanism
│   └── Restore stored in .pending_restore file, executed on SERVER_STARTING
└── BackupCommand.java    # All /backup commands (create, list, restore, delete, config, status, reload, cleanup)

Config path: FabricLoader.getConfigDir() + "/cacl2backup.json"
Backup dir: gameDir + "/backups"
World dir: gameDir + "/world"
```

## Key Behaviors

- **Restore flow**: User runs `/backup restore <backup>`, files extract to `world_temp_restore`, save `.pending_restore` file. If `autoRestartAfterRestore=true` in config, broadcasts restart message and auto-restarts after `restartDelaySeconds`. Otherwise user must stop server manually. On next SERVER_STARTING, `RestoreManager.executePendingRestore()` swaps directories and deletes pending file.
- **Backup naming**: `backup_YYYY-MM-DD_HH-mm-ss.zip` or `backup_YYYY-MM-DD_HH-mm-ss_label.zip`
- **Only server-side** (`"environment": "server"` in fabric.mod.json) — no client code

## Workflows

- `build.yml`: Runs on every push/PR, builds with Java 25, uploads JAR artifacts
- `release.yml`: Publishes GitHub Release on push to main/master, version from `gradle.properties` `mod_version`