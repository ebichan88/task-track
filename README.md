# TaskTrack — 業務タイムトラッキングツール

日々の業務内容と作業時間をローカルに記録し、振り返りと業務改善につなげるための Windows 向けデスクトップアプリケーションです。
データは PC 内の SQLite ファイルにのみ保存され、外部サーバーとの通信は行いません。

## 主な機能

- **タイマー**: 作業の開始・停止・切り替え・再開。計測中の作業を常時表示。同時に計測できる作業は1つ。
- **異常終了への対応**: 終了日時が保存されないまま終了した作業を次回起動時に検出し、「現在時刻で終了／終了日時を指定／破棄」を選択。
- **作業記録管理**: 手動登録・編集・削除、日付別の作業履歴表示（タイマー／手動の区別付き）。
- **マスタ管理**: プロジェクト・カテゴリ（有効／無効）・タグ。1つの作業記録に複数タグを付与可能。
- **集計**: 期間を指定して、日別・週別・カテゴリ別・プロジェクト別に作業時間と件数を集計。
- **日次振り返り**: 1日1件、成果・課題・判断・次のアクション・所感を記録（全項目任意）。
- **データ出力**: 指定期間のデータを CSV（UTF-8 BOM 付き）にし、ZIP にまとめて出力。
- **バックアップ・復元**: データベースのバックアップ作成と復元。

## 必要環境

- Java 21（JDK。ビルドと jpackage に必要）
- Windows（開発・テストは macOS / Linux でも可能）

## ビルドと実行

```bash
./gradlew build
```

```bash
./gradlew run
```

Windows では `gradlew.bat` を使用します。`build` は単体テストも実行します。

### 配布用アプリケーションの作成（jpackage）

```bash
./gradlew jpackage
```

`build/jpackage/TaskTrack/` に Java ランタイム同梱のアプリケーション（app-image）が作成されます。
フォルダごと配布し、`TaskTrack.exe` を実行します。

インストーラを作る場合は種類を指定します（Windows で `msi` / `exe` を作るには WiX Toolset が必要です）。

```bash
./gradlew jpackage -PjpackageType=msi
```

## データの保存場所

| 環境 | 場所 |
|---|---|
| Windows | `%APPDATA%\TaskTrack\tasktrack.db` |
| その他 | `~/.tasktrack/tasktrack.db` |

システムプロパティ `tasktrack.dataDir` で保存先フォルダを変更できます（例: `-Dtasktrack.dataDir=D:\data\tasktrack`）。

## 構成

```
src/main/java/tasktrack
├── Main.java / AppContext.java   起動処理と部品の組み立て
├── domain/   ドメイン層（作業記録・プロジェクト・カテゴリ・タグ・日次振り返り）
├── app/      アプリケーション層（タイマー制御・作業記録・集計・出力・バックアップなどの業務ロジック）
├── db/       データアクセス層（SQLite / JDBC、スキーマ定義）
└── ui/       UI 層（Swing）
```

- UI 層は `app` のサービスだけを呼び出し、業務ロジックを持ちません。
- 業務ロジックは `java.time.Clock` を注入して単体テストしています（`src/test`）。
- 未確定だった仕様の決定内容は [docs/design-decisions.md](docs/design-decisions.md) を参照してください。

## 依存関係の管理

実行時の外部ライブラリは SQLite JDBC（`org.xerial:sqlite-jdbc`）のみです。テストには JUnit 5 を使用します。
Gradle プラグインは Gradle 本体に同梱のコアプラグインのみを使用しています。

| 仕組み | 内容 |
|---|---|
| Gradle Wrapper | Gradle 本体のバージョンを固定（`gradle/wrapper/gradle-wrapper.properties`） |
| 依存ロック | 推移的依存を含むバージョンを `gradle.lockfile` で固定 |
| 依存検証 | 取得した成果物の SHA-256 を `gradle/verification-metadata.xml` と照合 |
| 取得元 | Maven Central のみ |
| 脆弱性の確認 | `.github/dependabot.yml`（GitHub の Dependabot alerts / 更新 PR） |

依存ライブラリを更新するときは、`build.gradle` のバージョンを変更したうえでロックと検証情報を更新します。

```bash
./gradlew dependencies --write-locks
```

```bash
./gradlew --write-verification-metadata sha256 build
```

IDE がソース JAR などを追加で取得して検証エラーになる場合も、同じコマンドで検証情報を更新してください。

現在の依存関係は次のコマンドで確認できます。

```bash
./gradlew dependencies --configuration runtimeClasspath
```

## CSV 出力の仕様（概要）

- ZIP ファイル名: `business-analysis-{開始日}_{終了日}.zip`
- 格納ファイル: `time_entries.csv` / `daily_reviews.csv` / `projects.csv` / `categories.csv` / `tags.csv` / `time_entry_tags.csv`
- UTF-8（BOM 付き）、CRLF、カンマ区切り、ヘッダーあり、全値をダブルクォートで囲む（RFC 4180 準拠）
- 日時は ISO 8601・オフセット付き（例: `2026-11-09T10:00:00+09:00`）
- 期間境界をまたぐ作業記録は期間内の部分のみを出力し、`entry_id` を維持したまま `segment_id` を付与
- 表計算ソフトで数式として解釈されうる値は、出力時のみ先頭に `'` を付けて無害化（DB の値は変更しない）
- 登録内容は匿名化されずそのまま出力されます。外部ツールへアップロードする際は、利用者自身が内容を確認してください。
