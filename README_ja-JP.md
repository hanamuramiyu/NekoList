<div align="center">

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**Discord連携機能付きのモダンなマルチプラットフォームホワイトリストプラグイン。**

[<kbd> <br> 🇺🇸 English (US) <br> </kbd>](README.md) | [<kbd> <br>🇯🇵 日本語 (ja-JP) [Current] <br> </kbd>](README_ja-JP.md) | [<kbd> <br> 🇨🇳 简体中文 (zh-CN) <br> </kbd>](README_zh-CN.md)

</div>

## ✨ 機能

### ユニバーサルホワイトリストシステム
- **永続的なプレイヤーデータ**: UUIDとニックネームを使用してプレイヤーを安全にリンクし、堅牢な検証を行います。
- **ニックネーム変更保護**: プレイヤーがMinecraftのユーザー名を変更しても、ホワイトリストに残り続けます。*(`online-mode=true`が必要)*
- **スマートフォールバック**: 正確性のためにまずUUIDをチェックし、必要に応じてニックネームにフォールバックします。

### Discord統合
- **インタラクティブなボットコマンド**: Discordからスラッシュコマンド (`/whitelist add`, `/whitelist remove` など) を使用してホワイトリストを直接管理できます。
- **ロールとユーザーの権限**: 特定のDiscordロールやユーザーIDにボットコマンドの使用を制限し、セキュリティを強化できます。

### マルチプラットフォーム対応
- **Bukkit & Forks**: Spigot、Paper、Purpur、およびその他のBukkitベースのサーバーに対応しています。
- **Velocity Proxy**: Velocityプロキシネットワークに完全対応しています。
- **統一された設定**: 単一の `config.yml` がすべてのサポートされているプラットフォームでシームレスに動作します。

---

## 🚀 インストール

### Bukkit/Spigot/Paper/Purpurサーバーの場合:
1.  [リリースページ](https://github.com/hanamuramiyu/NekoList/releases) から最新の `.jar` ファイルをダウンロードします。
2.  サーバーの `plugins` フォルダに `.jar` ファイルを配置します。
3.  サーバーを起動または再起動します。
4.  `plugins/NekoList/` ディレクトリにある生成された `config.yml` ファイルを見つけ、必要に応じて設定します。

### Velocity Proxyの場合:
1.  [リリースページ](https://github.com/hanamuramiyu/NekoList/releases) から最新の `.jar` ファイルをダウンロードします。
2.  プロキシの `plugins` フォルダに `.jar` ファイルを配置します。
3.  Velocityプロキシを起動または再起動します。
4.  `plugins/NekoList/` ディレクトリにある生成された `config.yml` ファイルを見つけ、必要に応じて設定します。

---

## ⚙️ 設定

メインの設定ファイルは `plugins/NekoList/config.yml` にあります。

設定例:

```yaml
# NekoList 設定
# 言語設定
language: "en-US"
# 利用可能な言語: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# Discordボット設定
discord-bot:
  # Discordボットを有効または無効にする
  enabled: false
  
  # Discordボットのトークン
  # 取得先: https://discord.com/developers/applications    
  token: "YOUR_BOT_TOKEN_HERE"
  
  # コマンドを使用できるロールIDのリスト
  # 例: ["123456789012345678", "987654321098765432"]
  # ロールIDの取得方法: Discordで開発者モードを有効にする -> ロールを右クリック -> IDをコピー
  allowed-roles: []
  
  # コマンドを使用できるユーザーIDのリスト
  # 例: ["123456789012345678", "987654321098765432"]
  # ユーザーIDの取得方法: Discordで開発者モードを有効にする -> ユーザーを右クリック -> IDをコピー
  allowed-users: []
```

---

## 🔧 コマンドと権限

### ゲーム内コマンド (`/whitelist`)
- `/whitelist help` - 利用可能なコマンドを表示します。
- `/whitelist on` - ホワイトリストを有効にします。
- `/whitelist off` - ホワイトリストを無効にします。
- `/whitelist list` - ホワイトリストに登録されたプレイヤーを表示します。
- `/whitelist add <player>` - プレイヤーをホワイトリストに追加します。
- `/whitelist remove <player>` - プレイヤーをホワイトリストから削除します。
- `/whitelist reload` - 設定ファイルを再読み込みします。

**権限ノード:** `nekolist.use`

### Discordスラッシュコマンド (ボットのセットアップが必要)
- `/ping` - ボットのレイテンシをテストします。
- `/whitelist add <player>` - プレイヤーをホワイトリストに追加します。
- `/whitelist remove <player>` - プレイヤーをホワイトリストから削除します。
- `/whitelist list` - ホワイトリストに登録されたプレイヤーを一覧表示します。
- `/whitelist status` - ホワイトリストの状態を確認します。

---

## 🌐 新しい言語の追加

1.  (プラグインを一度実行した後) `plugins/NekoList/lang/` ディレクトリに移動します。
2.  デフォルトの `en-US.yml` ファイルをコピーします。
3.  コピーしたファイルの名前を目的の言語コードに変更します (例: `fr-FR.yml`, `de-DE.yml`)。 使用可能なコードは既存の言語ファイルで確認できます。
4.  コピーしたファイルを編集し、値 (コロンの右側のテキスト) のみを翻訳し、キー (左側) は変更しないでください。
5.  `config.yml` の `language` 設定を新しい言語コードに更新します。

---

## 🏗️ ソースからのビルド

1.  リポジトリをクローンします:
    ```bash
    git clone https://github.com/hanamuramiyu/NekoList.git  
    cd NekoList
    ```
2.  Gradleを使用してプラグインJARファイルをビルドします:
    ```bash
    ./gradlew build
    ```
3.  コンパイルされたプラグインファイルは `build/libs/` ディレクトリに配置されます。

---

## 🤝 貢献

貢献を歓迎します！プルリクエストの送信、バグ報告、新機能の提案など、お気軽にどうぞ。

---

## 🐛 問題の報告

バグが見つかった場合や機能のリクエストがある場合は、[GitHub Issues](https://github.com/hanamuramiyu/NekoList/issues) ページで issue を作成してください。

---

## 📄 ライセンス

このプロジェクトはMITライセンスの下で公開されています - 詳細については[LICENSE](LICENSE)ファイルを参照してください。

---

<div align="center">

**Hanamura Miyu によって ❤️ を込めて作られました**

</div>