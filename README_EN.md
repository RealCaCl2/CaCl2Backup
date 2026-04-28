# CaCl2Backup

[中文](README.md) | English

A comprehensive Minecraft server backup mod designed for Fabric.

## Features

- **Scheduled Backups** - Configurable automatic backups at set intervals after server startup
- **Multi-threaded Compression** - Parallel compression using multiple threads for significantly faster backups
- **Automatic Cleanup** - Hourly automatic cleanup of old backups with both count and age limits
- **Full Restore** - Complete world save restoration from backups
- **Label System** - Add labels to backups for easy identification (special characters auto-replaced with underscores)
- **Index Operations** - Quickly select backups by index number without typing full filenames
- **Real-time Status** - View backup status, configuration, and backup list
- **Hot Reload Config** - Reload configuration without restarting the server
- **Auto-Restart Restore** - Optionally auto-restart the server after restoring a backup

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.15.0+
- Fabric API
- Java 25+

## Installation

1. Ensure Fabric Loader and Fabric API are installed
2. Place the mod JAR file into your server's `mods` folder
3. Start the server - the mod will automatically generate the configuration file

## Command Reference

All commands require OP privileges and use the `/backup` prefix.

### Basic Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/backup create [label]` | Create a manual backup (optional label) | `/backup create before_update` |
| `/backup list` | List all available backups (shows index numbers) | `/backup list` |
| `/backup status` | Show current configuration and status | `/backup status` |
| `/backup reload` | Reload configuration file | `/backup reload` |
| `/backup cleanup` | Manually clean up old backups | `/backup cleanup` |

### Restore & Delete

| Command | Description | Example |
|---------|-------------|---------|
| `/backup restore <index/filename>` | Restore world from specified backup | `/backup restore 1` |
| `/backup delete <index/filename>` | Delete specified backup | `/backup delete 2` |

**Tip:** Use `/backup list` to view backup index numbers, then use the index directly.

### Configuration Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/backup config` | Show all configuration options | `/backup config` |
| `/backup config interval <minutes>` | Set auto-backup interval | `/backup config interval 60` |
| `/backup config maxbackups <count>` | Set maximum backup count | `/backup config maxbackups 20` |
| `/backup config maxage <days>` | Set maximum backup age | `/backup config maxage 14` |
| `/backup config autobackup <on/off>` | Enable/disable auto backup | `/backup config autobackup on` |
| `/backup config autocleanup <on/off>` | Enable/disable auto cleanup | `/backup config autocleanup on` |
| `/backup config threads <count>` | Set compression thread count | `/backup config threads 4` |
| `/backup config level <1-9>` | Set compression level | `/backup config level 6` |
| `/backup config autorestart <on/off>` | Enable/disable auto-restart after restore | `/backup config autorestart on` |
| `/backup config restartdelay <seconds>` | Set auto-restart delay | `/backup config restartdelay 60` |
| `/backup config restartdelay <seconds>` | Set auto-restart delay | `/backup config restartdelay 60` |
| `/backup config restartmessage <message>` | Set restart message ({seconds} placeholder) | `/backup config restartmessage "Server will restart in {seconds} seconds..."` |
| `/backup config broadcastrestart <on/off>` | Enable/disable restart message broadcast | `/backup config broadcastrestart on` |

## Configuration File

Configuration file location: `config/cacl2backup.json`

```json
{
  "backupIntervalMinutes": 30,
  "maxBackups": 10,
  "compressionThreads": 8,
  "compressionLevel": 6,
  "autoBackupEnabled": true,
  "autoCleanupEnabled": true,
  "maxBackupAgeDays": 7,
  "backupFolderName": "backups",
  "broadcastBackupMessages": true,
  "saveOnBackup": true,
  "autoRestartAfterRestore": false,
  "restartDelaySeconds": 60,
  "restoreRestartMessage": "Server will restart in {seconds} seconds to complete restore...",
  "broadcastRestoreMessage": true
}
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `backupIntervalMinutes` | int | 30 | Auto-backup interval in minutes (minimum: 1) |
| `maxBackups` | int | 10 | Maximum number of backups to keep |
| `compressionThreads` | int | CPU cores | Number of compression threads |
| `compressionLevel` | int | 6 | Compression level (1-9), 1 is fastest with lowest ratio, 9 is slowest with highest ratio |
| `autoBackupEnabled` | boolean | true | Enable automatic backups |
| `autoCleanupEnabled` | boolean | true | Enable automatic cleanup of old backups |
| `maxBackupAgeDays` | int | 7 | Maximum backup age in days |
| `backupFolderName` | String | "backups" | Backup folder name |
| `broadcastBackupMessages` | boolean | true | Broadcast backup messages to all players |
| `saveOnBackup` | boolean | true | Save world before backup |
| `autoRestartAfterRestore` | boolean | false | Auto-restart server after restore |
| `restartDelaySeconds` | int | 60 | Auto-restart delay in seconds (minimum: 10) |
| `restoreRestartMessage` | String | "Server will restart in {seconds} seconds..." | Restart message template, {seconds} is replaced with delay |
| `broadcastRestoreMessage` | boolean | true | Broadcast restart message |

## Usage Guide

### Creating Backups

**Manual backup (recommended before important operations):**
```
/backup create before_major_update
```

**View backup list:**
```
/backup list
```
Example output:
```
[CaCl2Backup] Available backups (5):
1. 2024-02-20 10:30:00 [before_update] - 125.3 MB
2. 2024-02-19 22:00:00 [auto] - 124.8 MB
3. 2024-02-19 10:00:00 - 123.5 MB
...
```

### Restoring World

**Warning: Restore operation will overwrite the current world save!**

1. View backup list to get index number:
   ```
   /backup list
   ```

2. Restore using index:
   ```
   /backup restore 1
   ```

3. Behavior depends on configuration:
   - **autoRestartAfterRestore=false (default)**: Manually stop the server to complete restore
   - **autoRestartAfterRestore=true**: Mod auto-restarts server after specified delay

4. World is automatically replaced on next server startup

**Configure auto-restart behavior:**
```
/backup config autorestart on          # Enable auto-restart
/backup config restartdelay 60         # Set delay to 60 seconds
/backup config restartmessage "Server will restart in {seconds} seconds..."  # Custom message
/backup config broadcastrestart on     # Enable broadcast
```

### Deleting Backups

Delete using index:
```
/backup delete 2
```

## Backup Triggers

| Trigger | Description |
|---------|-------------|
| Scheduled Auto-Backup | Automatically executes at configured interval after server startup |
| Manual Command | Execute `/backup create [label]` |

**Note:** Backups are NOT created when shutting down the server.

## Backup File Naming

```
backup_YYYY-MM-DD_HH-mm-ss.zip              # No label
backup_YYYY-MM-DD_HH-mm-ss_label.zip        # With label
```

Examples:
- `backup_2024-02-20_10-30-00.zip`
- `backup_2024-02-20_10-30-00_before_update.zip`

**Label naming rules:**
- Allowed: letters `a-zA-Z`, digits `0-9`, underscore `_`, hyphen `-`
- Other characters (spaces, special symbols, etc.) are auto-replaced with underscore `_`

## Performance

### Multi-threaded Compression

The mod uses parallel compression to significantly speed up backups for large worlds:

| World Size | 1 Thread | 4 Threads | 8 Threads |
|------------|----------|-----------|-----------|
| 500 MB | ~45s | ~15s | ~10s |
| 2 GB | ~3min | ~1min | ~40s |
| 5 GB | ~8min | ~2.5min | ~1.5min |

### Compression Level Comparison

| Level | Ratio | Speed | Use Case |
|-------|-------|-------|----------|
| 1-3 | ~40% | Fastest | Frequent backups, SSD storage |
| 4-6 | ~50% | Balanced | Daily use (recommended) |
| 7-9 | ~55% | Slower | Limited storage space |

## Best Practices

1. **Regular Backup Checks**
   - Run `/backup list` regularly to confirm backups are created
   - Test restore process periodically to ensure backups are usable

2. **Reasonable Retention Policy**
   - Both `maxBackups` and `maxBackupAgeDays` apply simultaneously
   - Recommended: Keep at least 3-5 valid backups

3. **Manual Backup Before Important Operations**
   - Before mod updates
   - Before major building projects
   - Before server version upgrades

4. **Off-site Backups**
   - Periodically copy backup folder to another location
   - Consider cloud storage sync for backups

## Troubleshooting

### Backup Failed

**Problem:** `Backup failed: World directory not found`

**Solution:** Confirm the server world folder is named `world`.

### World Issues After Restore

**Problem:** Incomplete world data after restore

**Solution:**
1. Ensure server is restarted after restore completes
2. Check if backup file is complete (normal file size)
3. Try using a different backup file

### Permission Issues

**Problem:** Cannot execute commands

**Solution:** Ensure the player has been set as OP (use `/op player_name`).

## Development Info

- **Author:** CaCl2
- **Version:** 1.0.2
- **License:** MIT
- **Minecraft Version:** 26.1.2
- **Fabric API Version:** 0.146.1+

## Changelog

### 1.0.2
- Refactored restore flow: Removed `/backup restarter` command, auto-restart integrated into `/backup restore`
- New config options: `autoRestartAfterRestore`, `restartDelaySeconds`, `restoreRestartMessage`, `broadcastRestoreMessage`
- New config commands: `/backup config autorestart`, `/backup config restartdelay`, `/backup config restartmessage`, `/backup config broadcastrestart`

### 1.0.1
- Updated to Minecraft 26.1.2
- Added GitHub Release workflow
- Java version requirement raised to 25+

### 1.0.0
- Initial release
- Multi-threaded compression
- Scheduled auto-backups
- Automatic cleanup of old backups
- Full restore functionality
- Label system
- Index-based quick selection
- Hot reload configuration
