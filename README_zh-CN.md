<div align="center">

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**支持数据库、Folia兼容性和Discord集成的现代白名单插件。**

[<kbd> <br> 🇺🇸 English (US) <br> </kbd>](README.md) | [<kbd> <br> 🇯🇵 日本語 (ja-JP) <br> </kbd>](README_ja-JP.md) | [<kbd> <br>🇨🇳 简体中文 (zh-CN) [Current] <br> </kbd>](README_zh-CN.md)

</div>

## ✨ 功能

### 🗄️ 数据库与存储
- **双重存储选项**: 可使用基于文件的存储或MySQL/MariaDB数据库
- **自动回退**: 数据库连接失败时无缝切换到文件存储
- **数据持久性**: 确保服务器重启和插件重载时的数据完整性

### ⚡ 性能与兼容性
- **Folia服务器支持**: 使用自适应调度器与Folia服务器完全兼容
- **现代格式化**: 支持使用`<color>`标签进行富文本格式化的MiniMessage
- **Java 21**: 使用最新Java构建，提供最佳性能和安全性

### 🔐 安全与管理
- **基于UUID的验证**: 使用UUID和昵称回退进行高级玩家验证
- **智能数据同步**: 登录时自动玩家数据同步
- **连接池**: 使用HikariCP实现高效的数据库连接
- **基于角色的权限**: 通过用户和角色白名单实现细粒度Discord权限控制

---

## 🚀 安装

### 系统要求
- **Java 21** 或更高版本
- **Minecraft 1.21.1** 或更高版本
- **Bukkit/Paper/Purpur/Folia** 服务器

### 安装步骤:
1.  从[发布页面](https://github.com/hanamuramiyu/NekoList/releases)或[Modrinth](https://modrinth.com/plugin/nekolist)下载最新的`.jar`文件
2.  将`.jar`文件放入服务器的`plugins`文件夹
3.  启动或重启服务器
4.  根据需要配置`plugins/NekoList/config.yml`

---

## ⚙️ 配置

### 基本设置
```yaml
# NekoList 配置 v2.0.0
language: "en-US"
# 可用语言: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# 数据库设置
database:
  type: "file"  # "file" 或 "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "username"
    password: "password"
    table: "whitelist"
    use-ssl: false
    connection-timeout: 30000

# Discord 机器人设置
discord-bot:
  enabled: false
  token: "YOUR_BOT_TOKEN_HERE"
  allowed-roles: []
  allowed-users: []
```

### 数据库配置
- **文件模式**: 使用`whitelist.yml`进行存储（默认，推荐用于小型服务器）
- **MySQL模式**: 外部数据库存储（推荐用于大型网络）

---

## 🔧 命令与权限

### 游戏内命令 (`/whitelist`)
- `/whitelist help` - 显示可用命令
- `/whitelist on` - 启用白名单
- `/whitelist off` - 禁用白名单
- `/whitelist list` - 列出白名单中的玩家
- `/whitelist add <player>` - 将玩家添加到白名单
- `/whitelist remove <player>` - 从白名单中移除玩家
- `/whitelist reload` - 重新加载配置

**权限节点:**
- `nekolist.use` - 使用白名单命令
- `nekolist.bypass` - 绕过白名单检查
- `nekolist.admin` - 完全管理访问权限

### Discord 斜杠命令
- `/ping` - 检查机器人延迟
- `/whitelist add <player>` - 将玩家添加到白名单
- `/whitelist remove <player>` - 从白名单中移除玩家
- `/whitelist list` - 列出白名单中的玩家
- `/whitelist status` - 检查白名单状态
- `/whitelist reload` - 重新加载插件配置

---

## 🚨 v2.0.0重要注意事项

### ⚠️ 破坏性变更
1. **移除Velocity支持**: v2.0.0+仅支持Bukkit、Paper、Purpur和Folia服务器
2. **MiniMessage格式化**: 语言文件现在使用`<color>`标签而非传统的`&`代码
3. **需要Java 21**: 最低Java版本从17更新到21
4. **配置更新**: 部分配置选项已重新结构化

### 🔄 从v1.x迁移
1. **备份数据**: 复制`plugins/NekoList/whitelist.yml`
2. **更新语言文件**: 将`&`代码转换为MiniMessage格式:
   ```yaml
   # 旧格式 (v1.x)
   player-added: "&aPlayer %player% has been added to whitelist."
   
   # 新格式 (v2.0.0)
   player-added: "<green>Player %player% has been added to whitelist."
   ```
3. **Velocity用户**: 如需Velocity兼容性，请继续使用v1.2.1

---

## 🌐 添加新语言

1.  导航到`plugins/NekoList/lang/`目录
2.  复制`en-US.yml`作为模板
3.  重命名为您的语言代码（例如`fr-FR.yml`、`de-DE.yml`）
4.  使用**MiniMessage格式**（`<color>`标签）翻译所有值
5.  更新`config.yml`中的`language`设置

**MiniMessage标签示例:**
- `<red>` - 红色文本
- `<green>` - 绿色文本
- `<yellow>` - 黄色文本
- `<gray>` - 灰色文本
- `<gold>` - 金色文本
- `<bold>` - **粗体文本**

---

## 🏗️ 从源码构建

```bash
# 克隆仓库
git clone https://github.com/hanamuramiyu/NekoList.git
cd NekoList

# 构建插件
./gradlew build

# 输出文件: build/libs/NekoList-2.0.0.jar
```

**要求:**
- Java 21 JDK
- Gradle 9.2.0+

---

## 🤝 贡献

欢迎贡献！请遵循以下步骤:
1. Fork仓库
2. 创建功能分支
3. 进行更改
4. 提交Pull Request

请确保代码遵循现有风格并包含适当的测试。

---

## 🐛 问题报告

发现错误或有功能请求？请:
1. 检查现有[Issues](https://github.com/hanamuramiyu/NekoList/issues)
2. 创建描述清晰的新issue
3. 包含服务器日志和配置详情
4. 指定服务器类型和版本

---

## 📄 许可证

该项目根据MIT许可证授权 - 详情请参阅[LICENSE](LICENSE)文件。

---

<div align="center">

**由 Hanamura Miyu 倾心制作 ❤️**

*如需Velocity支持，请使用v1.2.1以获得兼容性。*

</div>