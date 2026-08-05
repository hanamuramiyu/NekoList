<div align="center">

<div style="background:transparent;padding:0;display:inline-block;">
  <a href="https://mimimofu.com" target="_blank">
    <img src="https://mimimofu.com/3.png" alt="MimiMofu" style="max-width:100%;height:auto;display:block;">
  </a>
</div>

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**A modern whitelist plugin with database support, Folia compatibility, and Discord integration.**

[<kbd> <br>🇺🇸 English (US) [Current] <br> </kbd>](README.md) | [<kbd> <br> 🇯🇵 日本語 (ja-JP) <br> </kbd>](README_ja-JP.md) | [<kbd> <br> 🇨🇳 简体中文 (zh-CN) <br> </kbd>](README_zh-CN.md)

</div>

## ✨ Features

### 🗄️ Database & Storage
- **Dual Storage Options**: Use either file-based storage or MySQL/MariaDB databases
- **Automatic Fallback**: Seamlessly switches to file storage if database connection fails
- **Data Persistence**: Guaranteed data integrity across server restarts and plugin reloads

### ⚡ Performance & Compatibility
- **Folia Server Support**: Full compatibility with Folia servers using adaptive schedulers
- **Modern Formatting**: MiniMessage support for rich text formatting with `<color>` tags
- **Java 21**: Built with the latest Java for optimal performance and security

### 🔐 Security & Management
- **UUID-Based Verification**: Advanced player verification using UUIDs with nickname fallback
- **Smart Data Sync**: Automatic player data synchronization on login
- **Connection Pooling**: Efficient database connections using HikariCP
- **Role-Based Permissions**: Granular Discord permission control with user and role whitelists

---

## 🚀 Installation

### System Requirements
- **Java 21** or higher
- **Minecraft 1.21.1** or higher
- **Bukkit/Paper/Purpur/Folia** server

### Installation Steps:
1.  Download the latest `.jar` file from [Releases](https://github.com/hanamuramiyu/NekoList/releases) or [Modrinth](https://modrinth.com/plugin/nekolist)
2.  Place the `.jar` file into your server's `plugins` folder
3.  Start or restart your server
4.  Configure `plugins/NekoList/config.yml` as needed

---

## ⚙️ Configuration

### Basic Setup
```yaml
# NekoList Configuration v2.0.0
language: "en-US"
# Available languages: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# Database settings
database:
  type: "file"  # "file" or "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "username"
    password: "password"
    table: "whitelist"
    use-ssl: false
    connection-timeout: 30000

# Discord Bot settings
discord-bot:
  enabled: false
  token: "YOUR_BOT_TOKEN_HERE"
  allowed-roles: []
  allowed-users: []
```

### Database Configuration
- **File Mode**: Uses `whitelist.yml` for storage (default, recommended for small servers)
- **MySQL Mode**: External database storage (recommended for large networks)

---

## 🔧 Commands & Permissions

### In-Game Commands (`/whitelist`)
- `/whitelist help` - Show available commands
- `/whitelist on` - Enable the whitelist
- `/whitelist off` - Disable the whitelist
- `/whitelist list` - List whitelisted players
- `/whitelist add <player>` - Add a player to whitelist
- `/whitelist remove <player>` - Remove a player from whitelist
- `/whitelist reload` - Reload configuration

**Permission Nodes:**
- `nekolist.use` - Use whitelist commands
- `nekolist.bypass` - Bypass whitelist checks
- `nekolist.admin` - Full administrative access

### Discord Slash Commands
- `/ping` - Check bot latency
- `/whitelist add <player>` - Add player to whitelist
- `/whitelist remove <player>` - Remove player from whitelist
- `/whitelist list` - List whitelisted players
- `/whitelist status` - Check whitelist status
- `/whitelist reload` - Reload plugin configuration

---

## 🚨 Important Notes for v2.0.0

### ⚠️ Breaking Changes
1. **Velocity Support Removed**: v2.0.0+ only supports Bukkit, Paper, Purpur, and Folia servers
2. **MiniMessage Formatting**: Language files now use `<color>` tags instead of legacy `&` codes
3. **Java 21 Required**: Minimum Java version updated from 17 to 21
4. **Configuration Updates**: Some config options have been restructured

### 🔄 Migration from v1.x
1. **Backup your data**: Copy `plugins/NekoList/whitelist.yml`
2. **Update language files**: Convert `&` codes to MiniMessage format:
   ```yaml
   # Old format (v1.x)
   player-added: "&aPlayer %player% has been added to whitelist."
   
   # New format (v2.0.0)
   player-added: "<green>Player %player% has been added to whitelist."
   ```
3. **Velocity Users**: Continue using v1.2.1 for Velocity compatibility

---

## 🌐 Adding New Languages

1.  Navigate to `plugins/NekoList/lang/` directory
2.  Copy `en-US.yml` as a template
3.  Rename to your language code (e.g., `fr-FR.yml`, `de-DE.yml`)
4.  Translate all values using **MiniMessage format** (`<color>` tags)
5.  Update `language` setting in `config.yml`

**Example MiniMessage tags:**
- `<red>` - Red text
- `<green>` - Green text  
- `<yellow>` - Yellow text
- `<gray>` - Gray text
- `<gold>` - Gold text
- `<bold>` - **Bold text**

---

## 🏗️ Building from Source

```bash
# Clone repository
git clone https://github.com/hanamuramiyu/NekoList.git
cd NekoList

# Build plugin
./gradlew build

# Output file: build/libs/NekoList-2.0.0.jar
```

**Requirements:**
- Java 21 JDK
- Gradle 9.2.0+

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a Pull Request

Please ensure your code follows the existing style and includes appropriate tests.

---

## 🐛 Issue Reporting

Found a bug or have a feature request? Please:
1. Check existing [Issues](https://github.com/hanamuramiyu/NekoList/issues)
2. Create a new issue with clear description
3. Include server logs and configuration details
4. Specify your server type and version

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ by Hanamura Miyu**

*Looking for Velocity support? Use v1.2.1 for Velocity compatibility.*

</div>
