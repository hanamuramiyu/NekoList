<div align="center">

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**一款现代化的多平台白名单插件，支持 Discord 集成。**

[<kbd> <br> 🇺🇸 English (US) <br> </kbd>](README.md) | [<kbd> <br> 🇯🇵 日本語 (ja-JP) <br> </kbd>](README_ja-JP.md) | [<kbd> <br>🇨🇳 简体中文 (zh-CN) [Current] <br> </kbd>](README_zh-CN.md)

</div>

## ✨ 功能

### 通用白名单系统
- **持久化玩家数据**: 使用 UUID 和昵称安全地链接玩家，进行可靠的验证。
- **昵称更改保护**: 玩家即使更改 Minecraft 用户名后，仍会保留在白名单中。*（需要 `online-mode=true`）*
- **智能回退**: 优先使用 UUID 进行准确验证，必要时回退到昵称。

### Discord 集成
- **交互式机器人命令**: 通过 Discord 中的斜杠命令（`/whitelist add`, `/whitelist remove` 等）直接管理您的白名单。
- **角色和用户权限**: 将机器人命令的使用限制为特定的 Discord 角色或用户 ID，以增强安全性。

### 多平台兼容性
- **Bukkit 及其衍生版**: 支持 Spigot、Paper、Purpur 和其他基于 Bukkit 的服务器。
- **Velocity 代理**: 完全支持 Velocity 代理网络。
- **统一配置**: 单个 `config.yml` 文件即可在所有支持的平台上无缝工作。

---

## 🚀 安装

### 对于 Bukkit/Spigot/Paper/Purpur 服务器：
1.  从 [发布页面](https://github.com/hanamuramiyu/NekoList/releases) 下载最新的 `.jar` 文件。
2.  将 `.jar` 文件放入服务器的 `plugins` 文件夹。
3.  启动或重启服务器。
4.  在 `plugins/NekoList/` 目录下找到生成的 `config.yml` 文件，并根据需要进行配置。

### 对于 Velocity 代理：
1.  从 [发布页面](https://github.com/hanamuramiyu/NekoList/releases) 下载最新的 `.jar` 文件。
2.  将 `.jar` 文件放入代理的 `plugins` 文件夹。
3.  启动或重启 Velocity 代理。
4.  在 `plugins/NekoList/` 目录下找到生成的 `config.yml` 文件，并根据需要进行配置。

---

## ⚙️ 配置

主配置文件位于 `plugins/NekoList/config.yml`。

配置示例:

```yaml
# NekoList 配置
# 语言设置
language: "en-US"
# 可用语言: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# Discord 机器人设置
discord-bot:
  # 启用或禁用 Discord 机器人
  enabled: false
  
  # 您的 Discord 机器人令牌
  # 获取地址: https://discord.com/developers/applications    
  token: "YOUR_BOT_TOKEN_HERE"
  
  # 可以使用机器人命令的角色 ID 列表
  # 示例: ["123456789012345678", "987654321098765432"]
  # 获取角色 ID 方法: 在 Discord 中启用开发者模式 -> 右键单击角色 -> 复制 ID
  allowed-roles: []
  
  # 可以使用机器人命令的用户 ID 列表
  # 示例: ["123456789012345678", "987654321098765432"]
  # 获取用户 ID 方法: 在 Discord 中启用开发者模式 -> 右键单击用户 -> 复制 ID
  allowed-users: []
```

---

## 🔧 命令与权限

### 游戏内命令 (`/whitelist`)
- `/whitelist help` - 显示可用命令。
- `/whitelist on` - 启用白名单。
- `/whitelist off` - 禁用白名单。
- `/whitelist list` - 显示白名单上的玩家。
- `/whitelist add <player>` - 将玩家添加到白名单。
- `/whitelist remove <player>` - 从白名单中移除玩家。
- `/whitelist reload` - 重新加载配置文件。

**权限节点:** `nekolist.use`

### Discord 斜杠命令 (需要机器人设置)
- `/ping` - 测试机器人的延迟。
- `/whitelist add <player>` - 将玩家添加到白名单。
- `/whitelist remove <player>` - 从白名单中移除玩家。
- `/whitelist list` - 列出白名单上的玩家。
- `/whitelist status` - 检查白名单状态。

---

## 🌐 添加新语言

1.  （运行插件一次后）导航至 `plugins/NekoList/lang/` 目录。
2.  复制默认的 `en-US.yml` 文件。
3.  将副本重命名为所需的语言代码（例如 `fr-FR.yml`, `de-DE.yml`）。您可以在现有的语言文件中找到代码。
4.  编辑复制的文件，仅翻译值（冒号右侧的文本），保持键（左侧）不变。
5.  在 `config.yml` 中将 `language` 设置更新为您新的语言代码。

---

## 🏗️ 从源码构建

1.  克隆仓库:
    ```bash
    git clone https://github.com/hanamuramiyu/NekoList.git  
    cd NekoList
    ```
2.  使用 Gradle 构建插件 JAR 文件:
    ```bash
    ./gradlew build
    ```
3.  编译好的插件文件将位于 `build/libs/` 目录中。

---

## 🤝 贡献

我们欢迎各种贡献！请随时提交拉取请求 (Pull Request)，报告错误或提出新功能建议。

---

## 🐛 问题报告

发现错误或有功能请求？请在我们的 [GitHub Issues](https://github.com/hanamuramiyu/NekoList/issues) 页面创建一个议题。

---

## 📄 许可证

该项目根据 MIT 许可证授权 - 详情请参阅 [LICENSE](LICENSE) 文件。

---

<div align="center">

**由 Hanamura Miyu 倾心制作 ❤️**

</div>