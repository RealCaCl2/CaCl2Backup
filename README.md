# CaCl2Backup

一个功能完善的Minecraft服务器备份模组，专为Fabric设计。

## 功能特性

- **自动定时备份** - 可配置的定时自动备份，服务器启动后按间隔执行
- **多线程压缩** - 使用多线程并行压缩，大幅提升备份速度
- **自动清理** - 自动清理过期备份，支持按数量和时间双重限制
- **完整恢复** - 支持从备份完整恢复世界存档
- **标签系统** - 为备份添加标签，便于识别和管理
- **序号操作** - 使用序号快速选择备份，无需输入完整文件名
- **实时状态** - 查看备份状态、配置信息和备份列表
- **热重载配置** - 无需重启服务器即可重载配置

## 系统要求

- Minecraft 1.21.x
- Fabric Loader 0.15.0+
- Fabric API
- Java 21+

## 安装

1. 确保已安装Fabric Loader和Fabric API
2. 将模组JAR文件放入服务器的`mods`文件夹
3. 启动服务器，模组会自动生成配置文件

## 命令参考

所有命令仅OP可执行，以`/backup`为前缀。

### 基础命令

| 命令 | 描述 | 示例 |
|------|------|------|
| `/backup create [标签]` | 创建手动备份（可选标签） | `/backup create before_update` |
| `/backup list` | 列出所有可用备份（显示序号） | `/backup list` |
| `/backup status` | 显示当前配置和状态 | `/backup status` |
| `/backup reload` | 重载配置文件 | `/backup reload` |
| `/backup cleanup` | 手动执行清理旧备份 | `/backup cleanup` |

### 恢复与删除

| 命令 | 描述 | 示例 |
|------|------|------|
| `/backup restore <序号/文件名>` | 从指定备份恢复世界 | `/backup restore 1` |
| `/backup delete <序号/文件名>` | 删除指定备份 | `/backup delete 2` |

**提示：** 可以使用 `/backup list` 查看备份序号，然后直接用序号操作。

### 配置命令

| 命令 | 描述 | 示例 |
|------|------|------|
| `/backup config` | 显示所有配置项 | `/backup config` |
| `/backup config interval <分钟>` | 设置自动备份间隔 | `/backup config interval 60` |
| `/backup config maxbackups <数量>` | 设置最大备份数量 | `/backup config maxbackups 20` |
| `/backup config maxage <天数>` | 设置备份最大保留天数 | `/backup config maxage 14` |
| `/backup config autobackup <on/off>` | 开启/关闭自动备份 | `/backup config autobackup on` |
| `/backup config autocleanup <on/off>` | 开启/关闭自动清理 | `/backup config autocleanup on` |
| `/backup config threads <数量>` | 设置压缩线程数 | `/backup config threads 4` |
| `/backup config level <1-9>` | 设置压缩级别 | `/backup config level 6` |

## 配置文件

配置文件位于：`config/cacl2backup.json`

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
  "saveOnBackup": true
}
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 描述 |
|--------|------|--------|------|
| `backupIntervalMinutes` | int | 30 | 自动备份间隔（分钟），最小值1 |
| `maxBackups` | int | 10 | 最大备份数量，超过此数量会自动删除最旧的备份 |
| `compressionThreads` | int | CPU核心数 | 压缩使用的线程数 |
| `compressionLevel` | int | 6 | 压缩级别（1-9），1最快压缩率最低，9最慢压缩率最高 |
| `autoBackupEnabled` | boolean | true | 是否启用自动备份 |
| `autoCleanupEnabled` | boolean | true | 是否启用自动清理旧备份 |
| `maxBackupAgeDays` | int | 7 | 备份最大保留天数 |
| `backupFolderName` | String | "backups" | 备份文件夹名称 |
| `broadcastBackupMessages` | boolean | true | 是否向所有玩家广播备份消息 |
| `saveOnBackup` | boolean | true | 备份前是否保存世界 |

## 使用指南

### 创建备份

**手动备份（推荐用于重要操作前）：**
```
/backup create before_major_update
```

**查看备份列表：**
```
/backup list
```
输出示例：
```
[CaCl2Backup] Available backups (5):
1. 2024-02-20 10:30:00 [before_update] - 125.3 MB
2. 2024-02-19 22:00:00 [auto] - 124.8 MB
3. 2024-02-19 10:00:00 - 123.5 MB
...
```

### 恢复世界

**警告：恢复操作会覆盖当前世界存档！**

1. 查看备份列表获取序号：
   ```
   /backup list
   ```

2. 使用序号恢复：
   ```
   /backup restore 1
   ```

3. **停止服务器**以完成恢复

4. 再次启动服务器，世界将恢复到备份状态

### 删除备份

使用序号删除：
```
/backup delete 2
```

## 备份触发方式

| 触发方式 | 说明 |
|----------|------|
| 定时自动备份 | 服务器启动后，按配置的间隔时间自动执行 |
| 手动命令 | 执行 `/backup create [标签]` |

**注意：** 关闭服务器时不会自动创建备份。

## 备份文件命名规则

```
backup_YYYY-MM-DD_HH-mm-ss.zip           # 无标签备份
backup_YYYY-MM-DD_HH-mm-ss_标签.zip      # 带标签备份
```

示例：
- `backup_2024-02-20_10-30-00.zip`
- `backup_2024-02-20_10-30-00_before_update.zip`

## 性能说明

### 多线程压缩

模组使用并行压缩算法，可显著提升大世界的备份速度：

| 世界大小 | 单线程 | 4线程 | 8线程 |
|----------|--------|-------|-------|
| 500 MB | ~45秒 | ~15秒 | ~10秒 |
| 2 GB | ~3分钟 | ~1分钟 | ~40秒 |
| 5 GB | ~8分钟 | ~2.5分钟 | ~1.5分钟 |

### 压缩级别对比

| 级别 | 压缩率 | 速度 | 适用场景 |
|------|--------|------|----------|
| 1-3 | ~40% | 最快 | 频繁备份、SSD存储 |
| 4-6 | ~50% | 平衡 | 日常使用（推荐） |
| 7-9 | ~55% | 较慢 | 存储空间紧张 |

## 最佳实践

1. **定期检查备份**
   - 定期运行 `/backup list` 确认备份正常创建
   - 定期测试恢复流程确保备份可用

2. **合理设置保留策略**
   - `maxBackups` 和 `maxBackupAgeDays` 同时生效
   - 建议保留至少3-5个有效备份

3. **重要操作前手动备份**
   - 更新模组前
   - 重大建筑项目前
   - 服务器版本升级前

4. **异地备份**
   - 定期将备份文件夹复制到其他位置
   - 考虑使用云存储同步备份

## 故障排除

### 备份失败

**问题：** `Backup failed: World directory not found`

**解决：** 确认服务器世界文件夹名称为 `world`。

### 恢复后世界异常

**问题：** 恢复后世界数据不完整

**解决：**
1. 确保恢复完成后重启服务器
2. 检查备份文件是否完整（文件大小是否正常）
3. 尝试使用其他备份文件

### 权限问题

**问题：** 无法执行命令

**解决：** 确保玩家已被设置为OP（使用 `/op 玩家名`）。

## 开发信息

- **作者：** CaCl2
- **版本：** 1.0.0
- **许可证：** MIT
- **Minecraft版本：** 1.21.x
- **Fabric API版本：** 0.141.3+

## 更新日志

### 1.0.0
- 初始版本发布
- 多线程压缩支持
- 自动定时备份
- 自动清理旧备份
- 完整恢复功能
- 标签系统
- 序号快速选择
- 热重载配置
