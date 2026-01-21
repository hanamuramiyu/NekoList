<div align="center">

# NekoList
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Modrinth](https://img.shields.io/modrinth/dt/nekolist?label=downloads&logo=modrinth)](https://modrinth.com/plugin/nekolist)
[![GitHub Repo stars](https://img.shields.io/github/stars/hanamuramiyu/NekoList?style=social)](https://github.com/hanamuramiyu/NekoList)

**データベース対応、Folia互換、Discord統合を備えた現代的なホワイトリストプラグイン。**

[<kbd> <br> 🇺🇸 English (US) <br> </kbd>](README.md) | [<kbd> <br>🇯🇵 日本語 (ja-JP) [Current] <br> </kbd>](README_ja-JP.md) | [<kbd> <br> 🇨🇳 简体中文 (zh-CN) <br> </kbd>](README_zh-CN.md)

</div>

## ✨ 機能

### 🗄️ データベースとストレージ
- **デュアルストレージオプション**: ファイルベースのストレージまたはMySQL/MariaDBデータベースを使用可能
- **自動フォールバック**: データベース接続が失敗した場合、ファイルストレージにシームレスに切り替え
- **データ永続性**: サーバー再起動やプラグイン再読み込みを超えたデータ整合性を保証

### ⚡ パフォーマンスと互換性
- **Foliaサーバー対応**: 適応型スケジューラを使用したFoliaサーバーとの完全互換性
- **モダンな書式設定**: `<color>`タグを使用したリッチテキスト書式設定のMiniMessage対応
- **Java 21**: 最新のJavaで構築され、最適なパフォーマンスとセキュリティを提供

### 🔐 セキュリティと管理
- **UUIDベースの検証**: ニックネームフォールバックを備えたUUIDを使用した高度なプレイヤー検証
- **スマートデータ同期**: ログイン時の自動プレイヤーデータ同期
- **コネクションプーリング**: HikariCPを使用した効率的なデータベース接続
- **ロールベースの権限**: ユーザーとロールのホワイトリストによる詳細なDiscord権限制御

---

## 🚀 インストール

### システム要件
- **Java 21** 以上
- **Minecraft 1.21.1** 以上
- **Bukkit/Paper/Purpur/Folia** サーバー

### インストール手順:
1.  [リリースページ](https://github.com/hanamuramiyu/NekoList/releases) または [Modrinth](https://modrinth.com/plugin/nekolist) から最新の `.jar` ファイルをダウンロード
2.  サーバーの `plugins` フォルダに `.jar` ファイルを配置
3.  サーバーを起動または再起動
4.  `plugins/NekoList/config.yml` を必要に応じて設定

---

## ⚙️ 設定

### 基本設定
```yaml
# NekoList 設定 v2.0.0
language: "en-US"
# 利用可能な言語: en-US, en-GB, es-ES, es-419, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW

# データベース設定
database:
  type: "file"  # "file" または "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "username"
    password: "password"
    table: "whitelist"
    use-ssl: false
    connection-timeout: 30000

# Discordボット設定
discord-bot:
  enabled: false
  token: "YOUR_BOT_TOKEN_HERE"
  allowed-roles: []
  allowed-users: []
```

### データベース設定
- **ファイルモード**: ストレージに `whitelist.yml` を使用（デフォルト、小規模サーバーに推奨）
- **MySQLモード**: 外部データベースストレージ（大規模ネットワークに推奨）

---

## 🔧 コマンドと権限

### ゲーム内コマンド (`/whitelist`)
- `/whitelist help` - 利用可能なコマンドを表示
- `/whitelist on` - ホワイトリストを有効化
- `/whitelist off` - ホワイトリストを無効化
- `/whitelist list` - ホワイトリスト登録プレイヤーを表示
- `/whitelist add <player>` - プレイヤーをホワイトリストに追加
- `/whitelist remove <player>` - プレイヤーをホワイトリストから削除
- `/whitelist reload` - 設定を再読み込み

**権限ノード:**
- `nekolist.use` - ホワイトリストコマンドを使用
- `nekolist.bypass` - ホワイトリストチェックをバイパス
- `nekolist.admin` - 完全な管理アクセス

### Discordスラッシュコマンド
- `/ping` - ボットの遅延を確認
- `/whitelist add <player>` - プレイヤーをホワイトリストに追加
- `/whitelist remove <player>` - プレイヤーをホワイトリストから削除
- `/whitelist list` - ホワイトリスト登録プレイヤーを表示
- `/whitelist status` - ホワイトリストステータスを確認
- `/whitelist reload` - プラグイン設定を再読み込み

---

## 🚨 v2.0.0の重要な注意点

### ⚠️ 破壊的変更
1. **Velocityサポート削除**: v2.0.0以降はBukkit、Paper、Purpur、Foliaサーバーのみをサポート
2. **MiniMessage書式設定**: 言語ファイルは従来の `&` コードではなく `<color>` タグを使用
3. **Java 21必須**: 最低Javaバージョンが17から21に更新
4. **設定更新**: 一部の設定オプションが再構築されました

### 🔄 v1.xからの移行
1. **データのバックアップ**: `plugins/NekoList/whitelist.yml` をコピー
2. **言語ファイルの更新**: `&` コードをMiniMessage形式に変換:
   ```yaml
   # 旧形式 (v1.x)
   player-added: "&aPlayer %player% has been added to whitelist."
   
   # 新形式 (v2.0.0)
   player-added: "<green>Player %player% has been added to whitelist."
   ```
3. **Velocityユーザー**: Velocity互換性には引き続きv1.2.1を使用

---

## 🌐 新しい言語の追加

1.  `plugins/NekoList/lang/` ディレクトリに移動
2.  テンプレートとして `en-US.yml` をコピー
3.  言語コードにリネーム（例: `fr-FR.yml`, `de-DE.yml`）
4.  **MiniMessage形式** (`<color>`タグ) を使用してすべての値を翻訳
5.  `config.yml` の `language` 設定を更新

**MiniMessageタグの例:**
- `<red>` - 赤いテキスト
- `<green>` - 緑のテキスト
- `<yellow>` - 黄色のテキスト
- `<gray>` - 灰色のテキスト
- `<gold>` - 金色のテキスト
- `<bold>` - **太字テキスト**

---

## 🏗️ ソースからのビルド

```bash
# リポジトリをクローン
git clone https://github.com/hanamuramiyu/NekoList.git
cd NekoList

# プラグインをビルド
./gradlew build

# 出力ファイル: build/libs/NekoList-2.0.0.jar
```

**要件:**
- Java 21 JDK
- Gradle 9.2.0+

---

## 🤝 貢献

貢献を歓迎します！以下の手順に従ってください:
1. リポジトリをフォーク
2. 機能ブランチを作成
3. 変更を加える
4. プルリクエストを提出

コードが既存のスタイルに従い、適切なテストを含んでいることを確認してください。

---

## 🐛 問題の報告

バグを見つけた場合や機能リクエストがある場合は:
1. 既存の [Issues](https://github.com/hanamuramiyu/NekoList/issues) を確認
2. 明確な説明を含む新しいissueを作成
3. サーバーログと設定詳細を含める
4. サーバータイプとバージョンを指定

---

## 📄 ライセンス

このプロジェクトはMITライセンスの下で公開されています - 詳細については[LICENSE](LICENSE)ファイルを参照してください。

---

<div align="center">

**Hanamura Miyu によって ❤️ を込めて作られました**

*Velocityサポートが必要な場合は、互換性のためにv1.2.1を使用してください。*

</div>