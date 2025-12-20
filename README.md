<div align="center">

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**A modern, multi-platform whitelist plugin with Discord integration.**

[<kbd> <br>🇺🇸 English (US) [Current] <br> </kbd>](README.md) | [<kbd> <br> 🇯🇵 日本語 (ja-JP) <br> </kbd>](README_ja-JP.md) | [<kbd> <br> 🇨🇳 简体中文 (zh-CN) <br> </kbd>](README_zh-CN.md)

</div>

## ✨ Features

### Universal Whitelist System
- **Persistent Player Data**: Securely links players using UUIDs and nicknames for robust verification.
- **Nickname Change Protection**: Players remain whitelisted even after changing their Minecraft username. *(Requires `online-mode=true`)*
- **Smart Fallback**: Prioritizes UUID checks for accuracy, falling back to nickname when necessary.

### Discord Integration
- **Interactive Bot Commands**: Manage your whitelist directly from Discord using slash commands (`/whitelist add`, `/whitelist remove`, etc.).
- **Role & User Permissions**: Restrict bot command usage to specific Discord roles or user IDs for enhanced security.

### Multi-Platform Compatibility
- **Bukkit & Forks**: Works with Spigot, Paper, Purpur, and other Bukkit-based servers.
- **Velocity Proxy**: Offers full support for Velocity proxy networks.
- **Unified Configuration**: A single `config.yml` works seamlessly across all supported platforms.

---

## 🚀 Installation

### For Bukkit/Spigot/Paper/Purpur Servers:
1.  Download the latest `.jar` file from the [Releases](https://github.com/hanamuramiyu/NekoList/releases) page.
2.  Place the `.jar` file into your server's `plugins` folder.
3.  Start or restart your server.
4.  Locate the generated `config.yml` file in the `plugins/NekoList/` directory and configure as needed.

### For Velocity Proxy:
1.  Download the latest `.jar` file from the [Releases](https://github.com/hanamuramiyu/NekoList/releases) page.
2.  Place the `.jar` file into your proxy's `plugins` folder.
3.  Start or restart your Velocity proxy.
4.  Locate the generated `config.yml` file in the `plugins/NekoList/` directory and configure as needed.

---

## ⚙️ Configuration

The main configuration file is located at `plugins/NekoList/config.yml`.

Example configuration:

```yaml
# NekoList Configuration
# Language settings
language: "en-US"
# Available languages: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# Discord Bot settings
discord-bot:
  # Enable or disable Discord bot
  enabled: false
  
  # Your Discord bot token
  # Get it from: https://discord.com/developers/applications  
  token: "YOUR_BOT_TOKEN_HERE"
  
  # List of role IDs that can use bot commands
  # Example: ["123456789012345678", "987654321098765432"]
  # To get role ID: Enable Developer Mode in Discord -> Right-click role -> Copy ID
  allowed-roles: []
  
  # List of user IDs that can use bot commands
  # Example: ["123456789012345678", "987654321098765432"]
  # To get user ID: Enable Developer Mode in Discord -> Right-click user -> Copy ID
  allowed-users: []
```

---

## 🔧 Commands & Permissions

### In-Game Commands (`/whitelist`)
- `/whitelist help` - Displays available commands.
- `/whitelist on` - Enables the whitelist.
- `/whitelist off` - Disables the whitelist.
- `/whitelist list` - Shows whitelisted players.
- `/whitelist add <player>` - Adds a player to the whitelist.
- `/whitelist remove <player>` - Removes a player from the whitelist.
- `/whitelist reload` - Reloads the configuration file.

**Permission Node:** `nekolist.use`

### Discord Slash Commands (requires bot setup)
- `/ping` - Tests the bot's latency.
- `/whitelist add <player>` - Adds a player to the whitelist.
- `/whitelist remove <player>` - Removes a player from the whitelist.
- `/whitelist list` - Lists whitelisted players.
- `/whitelist status` - Checks the whitelist status.

---

## 🌐 Adding New Languages

1.  Navigate to the `plugins/NekoList/lang/` directory (after running the plugin once).
2.  Copy the default `en-US.yml` file.
3.  Rename the copy to your desired language code (e.g., `fr-FR.yml`, `de-DE.yml`). You can find codes in the existing language files.
4.  Edit the copied file, translating only the values (the text on the right side of the colon), keeping the keys (on the left) unchanged.
5.  Update the `language` setting in `config.yml` to your new language code.

---

## 🏗️ Building from Source

1.  Clone the repository:
    ```bash
    git clone https://github.com/hanamuramiyu/NekoList.git
    cd NekoList
    ```
2.  Build the plugin JAR file using Gradle:
    ```bash
    ./gradlew build
    ```
3.  The compiled plugin file will be located in the `build/libs/` directory.

---

## 🤝 Contributing

We welcome contributions! Please feel free to submit pull requests, report bugs, or suggest new features.

---

## 🐛 Issue Reporting

Found a bug or have a feature request? Please create an issue on our [GitHub Issues](https://github.com/hanamuramiyu/NekoList/issues) page.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ by Hanamura Miyu**

</div>
