# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
日本語で話す

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run all unit tests
./gradlew test

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "net.kigawa.kalender.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Architecture

Single-module Android app (`app/`) using Jetpack Compose + Material3.

**Entry point:** `MainActivity` sets up `KalenderTheme` and renders `KalenderApp`.

**Navigation:** `KalenderApp` uses `NavigationSuiteScaffold` (adaptive navigation — switches between bottom bar and rail depending on screen size) driven by the `AppDestinations` enum. Adding a new top-level destination means adding an entry to `AppDestinations` with a label and `ImageVector` icon.

**Theme:** `KalenderTheme` in `ui/theme/` supports dynamic color (Android 12+) with static light/dark fallbacks. Wrap all Composable previews in `KalenderTheme`.

**Key versions:**
- AGP 9.0.1 / Kotlin 2.0.21
- Compose BOM 2025.07.00
- Min SDK 24 / Target SDK 36

All dependency versions are centralized in `gradle/libs.versions.toml`.

## Development Workflow

### ブランチ戦略
- `main` — リリース可能な状態を常に維持
- `develop` — 統合ブランチ（feature を取り込む先）
- `feature/<topic>` — 新機能（例: `feature/calendar-view`）
- `fix/<topic>` — バグ修正（例: `fix/navigation-state`）
- `chore/<topic>` — ビルド・依存関係・設定変更

### コミット規約
[Conventional Commits](https://www.conventionalcommits.org/) に従い `<type>(<scope>): <subject>` 形式で記述する。

Types: `feat` / `fix` / `refactor` / `test` / `chore` / `docs` / `style`

### Issue 管理

#### Issue 作成ルール
- タイトルは「何をするか」を動詞から始める日本語で記述する（例: `カレンダー画面にスワイプ操作を追加する`）
- ラベルは以下を使い分ける:
  - `feat` — 新機能
  - `fix` — バグ修正
  - `chore` — ビルド・依存関係・設定
  - `docs` — ドキュメント
- 作業量が大きい場合はサブタスクを箇条書きで本文に記載する

#### ブランチと Issue の紐づけ
- ブランチ名に Issue 番号を含める: `feature/<番号>-<topic>`（例: `feature/11-issue-convention`）
- これにより PR から Issue を追跡しやすくする

#### 作業フロー
1. 作業開始時に自分を assignee にセットする
2. 作業完了後、PR マージ時に `Closes #<issue番号>` を PR 本文に記載して自動クローズする
3. 作業途中で issue が不要になった場合も、理由をコメントしてからクローズする

### PR 作成規約

#### タイトル
- 日本語で「何をしたか」を動詞から始める形で記述する（例: `カレンダー画面にスワイプ操作を追加する`）
- Conventional Commits の type をプレフィックスとして付ける（例: `feat: カレンダー画面にスワイプ操作を追加する`）

#### 本文構成
PR 本文は日本語で以下の構成で記述する:

```
## 概要
変更の目的と背景を簡潔に説明する。

## 変更内容
- 変更点を箇条書きで列挙する

## テスト計画
- [ ] 確認した項目をチェックリスト形式で記載する

Closes #<issue番号>
```

#### 運用ルール
- 対応する Issue が存在する場合は `Closes #<issue番号>` を本文末尾に記載してマージ時に自動クローズする
- マージ方法は **Squash merge** を使用してコミット履歴をクリーンに保つ
- マージ前にセルフレビューを行い、差分が意図通りであることを確認する

### 変更前チェックリスト
1. `./gradlew lint` — 警告ゼロを目標にする
2. `./gradlew test` — ユニットテスト全通過
3. Composable には `@Preview` を付け Android Studio で目視確認

### 依存関係の追加
- バージョンは必ず `gradle/libs.versions.toml` に集約する
- Compose 系は `androidx.compose.bom` で統一管理し、個別バージョン指定しない
- 新ライブラリ追加時は `./gradlew dependencies` で重複・競合を確認

## Code Conventions

### パッケージ構成
`net.kigawa.kalender` 配下を機能レイヤーで分割する:
- `ui/screen/` — 各画面の Composable（1画面1ファイル）
- `ui/component/` — 再利用可能な UI コンポーネント
- `ui/theme/` — Color / Type / Theme
- `viewmodel/` — ViewModel（状態管理）
- `data/` — Repository / データソース
- `model/` — ドメインモデル（データクラス）

### Compose ルール
- 画面単位の Composable は `Screen` サフィックス（例: `CalendarScreen`）
- `@Composable` 関数は副作用を持たない。副作用は `LaunchedEffect` / `SideEffect` に閉じ込める
- 状態は `ViewModel` で持つ。ローカル一時状態のみ `remember`/`rememberSaveable` を使う
- プレビューは必ず `KalenderTheme { }` でラップする
- `Modifier` パラメータは最後の必須引数として定義し、デフォルトを `Modifier` にする

### ナビゲーション
- トップレベル画面の追加は `AppDestinations` enum にエントリを追加するだけ
- 画面遷移ロジックは Composable に直接書かず、コールバックで親に移譲する

### ViewModel / 状態管理
- UI 状態は `data class` の sealed class か単一の `UiState` で表現する
- `StateFlow` で状態を公開し、`collectAsStateWithLifecycle()` で購読する
- ViewModel からは `Context` を参照しない（`Application` が必要な場合は `AndroidViewModel`）

### テスト方針
- ビジネスロジックはユニットテストで網羅（`app/src/test/`）
- UI の回帰はプレビューで目視 + 重要フローのみ instrumented テスト
- テストクラス名は `<TargetClass>Test`、テスト関数名は `when_<condition>_then_<expected>` 形式