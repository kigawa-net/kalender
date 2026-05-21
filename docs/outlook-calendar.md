# Outlook カレンダー連携 設計ドキュメント

## 概要

Microsoft 365 / Outlook のカレンダーを Microsoft Graph API 経由で取得し、既存の Android CalendarContract ベースのカレンダーと統合表示する。

---

## 前提条件

### Azure AD アプリ登録

Microsoft Entra 管理センター (旧 Azure AD ポータル) でアプリを登録し、以下の設定を行う。

| 設定項目 | 内容 |
|---------|------|
| プラットフォーム | Android |
| パッケージ名 | `net.kigawa.kalender` |
| 署名ハッシュ | デバッグ用・リリース用の SHA-1 を登録 |
| API アクセス許可 | `Calendars.Read`（委任アクセス許可） |
| テナント | `common`（個人・職場・学校アカウント） |

登録後に発行されるアプリケーション (クライアント) ID を `strings.xml` および `app/src/main/res/raw/msal_config.json` に保存する。

---

## 開発者コンソール設定チェックリスト

### Microsoft Entra (Azure AD)
1. **プラットフォーム設定**: Android を追加。
2. **パッケージ名**: `net.kigawa.kalender`
3. **署名ハッシュ**: ローカルの `debug.keystore` から取得した `NzMGGgqiavH14j5mkahAmPCPc9E=` を登録。
4. **リダイレクト URI**: 自動生成される `msauth://net.kigawa.kalender/NzMGGgqiavH14j5mkahAmPCPc9E=` を確認。
5. **API アクセス許可**: `Microsoft Graph` -> `Calendars.Read` を追加し、「代表者の同意」は不要（個人の場合はユーザーが同意）。

### Google Cloud Console
1. **API の有効化**: 「Google Calendar API」を有効化。
2. **OAuth 同意画面**: 外部または内部を選択し、`https://www.googleapis.com/auth/calendar.readonly` スコープを追加。
3. **認証情報 (Android)**:
   - パッケージ名: `net.kigawa.kalender`
   - SHA-1 指紋: `37:33:06:1A:0A:A2:6A:F1:F5:E2:3E:66:91:A8:40:98:F0:8F:73:D1`
4. **認証情報 (Web)**:
   - 「ウェブ クライアント ID」を作成。
   - **重要**: Android クライアントと同じプロジェクト内で作成すること。
   - 発行された ID を `strings.xml` の `google_web_client_id` に設定。

---

## トラブルシューティング

### Google 認証で「開発者エラー (10)」が出る場合
- Google Cloud Console で登録した SHA-1 指紋が正しいか再確認してください。
- デバッグ用の SHA-1 (`37:33:06:1A:0A:A2:6A:F1:F5:E2:3E:66:91:A8:40:98:F0:8F:73:D1`) が登録されているか確認してください。
- パッケージ名 `net.kigawa.kalender` が完全に一致しているか確認してください。
- `google_web_client_id` が、Android クライアントを登録したのと同じプロジェクトの「ウェブ クライアント ID」であることを確認してください。

### Microsoft (Outlook) 認証でエラーが出る場合
- Microsoft Entra 管理センターで、プラットフォーム「Android」が追加されているか確認してください。
- 署名ハッシュに `NzMGGgqiavH14j5mkahAmPCPc9E=` が登録されているか確認してください。
- リダイレクト URI が `msauth://net.kigawa.kalender/NzMGGgqiavH14j5mkahAmPCPc9E=` になっているか確認してください（末尾の `=` を含みます）。
- `app/src/main/res/raw/msal_config.json` の `redirect_uri` は **`%3D` 形式** (`msauth://net.kigawa.kalender/NzMGGgqiavH14j5mkahAmPCPc9E%3D`) にする必要があります。MSAL は `Uri.Builder.appendPath()` でハッシュをパスに追加するため `=` を `%3D` に自動エンコードします。MSAL の内部生成 URI と config の値を `equalsIgnoreCase` で比較するため、`%3D` でなければ初期化時に例外が発生します。Azure Portal の redirect URI も同じく `msauth://net.kigawa.kalender/NzMGGgqiavH14j5mkahAmPCPc9E%3D` で登録してください。

---

## 認証フロー

### 使用ライブラリ

Microsoft Authentication Library for Android (MSAL) を使用する。

### OAuth 2.0 認可コードフロー（PKCE）

```
アプリ → MSAL → Microsoft ログイン画面 → 認可コード発行
      → MSAL がアクセストークン・リフレッシュトークンを取得・保存
      → Graph API リクエスト時に MSAL がトークンを自動付与
```

- アクセストークンの有効期限は通常 1 時間。MSAL がキャッシュと自動更新を管理する。
- ユーザーが明示的にサインアウトするまでリフレッシュトークンで継続的に更新される。
- デバイスに複数の Microsoft アカウントを追加できる（マルチアカウント対応）。

### トークン取得戦略

| シナリオ | 動作 |
|---------|------|
| 初回起動 | インタラクティブ認証（ブラウザ/システム認証画面を起動） |
| 2回目以降 | サイレント取得（キャッシュ/リフレッシュ。失敗時のみインタラクティブ） |
| アカウント切り替え | アカウント選択画面を表示し、選択アカウントでサイレント取得 |

---

## Microsoft Graph API 連携

### エンドポイント

| 操作 | パス |
|------|------|
| カレンダー一覧取得 | `GET /me/calendars` |
| イベント一覧取得（週指定） | `GET /me/calendarView?startDateTime=&endDateTime=` |
| イベント一覧取得（カレンダー指定） | `GET /me/calendars/{id}/calendarView?startDateTime=&endDateTime=` |

### 取得フィールド

`$select` クエリパラメータで必要フィールドのみ取得する。

**カレンダー:**

| フィールド | 用途 |
|-----------|------|
| `id` | カレンダー識別子 |
| `name` | カレンダー名 |
| `color` | 表示色 |
| `owner.address` | 所有者メールアドレス |

**イベント (calendarView):**

| フィールド | 用途 |
|-----------|------|
| `id` | イベント識別子 |
| `subject` | タイトル |
| `start.dateTime` / `start.timeZone` | 開始日時・タイムゾーン |
| `end.dateTime` / `end.timeZone` | 終了日時・タイムゾーン |
| `isAllDay` | 終日イベントフラグ |
| `body.content` | 説明（text 形式を指定） |
| `location.displayName` | 場所 |
| `calendar@odata.bind` | 所属カレンダー識別子 |

### ページネーション

Graph API は 1 回の応答に最大 250 件を返す。`@odata.nextLink` が存在する場合は続きを取得してすべてのイベントを収集する。

### エラーハンドリング

| HTTP ステータス | 対処 |
|----------------|------|
| 401 Unauthorized | MSAL でインタラクティブ再認証を促す |
| 429 Too Many Requests | `Retry-After` ヘッダーの秒数待機後にリトライ |
| 503 / 504 | Exponential backoff でリトライ（最大 3 回） |

---

## データモデル

### 統合イベントモデル

既存の `CalendarEvent` に `source` フィールドを追加して出自を区別する。Outlook 由来は `OUTLOOK` を設定する。

**追加フィールド:**

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `source` | EventSource（enum） | `LOCAL`（CalendarContract）/ `OUTLOOK` |
| `outlookEventId` | String? | Graph API のイベント ID（Outlook のみ） |
| `outlookCalendarId` | String? | Graph API のカレンダー ID（Outlook のみ） |

### 統合カレンダーモデル

既存の `UserCalendar` に同様の `source` と `outlookCalendarId` を追加する。

---

## アーキテクチャ

### レイヤー構成

```
UI (Compose)
  └── WeeklyCalendarScreen
        └── WeeklyCalendarViewModel
              └── CalendarRepository（統合）
                    ├── CalendarDataSource        ← Android CalendarContract
                    ├── CalendarLocalSource       ← Room キャッシュ
                    └── OutlookCalendarDataSource ← Graph API
                          └── MsalAuthManager    ← MSAL トークン管理
```

### OutlookCalendarDataSource

- `MsalAuthManager` からアクセストークンを取得して Graph API を呼び出す。
- 週単位のリクエスト（`calendarView`）を行い、ページネーションを処理する。
- レスポンスを `CalendarEvent` / `UserCalendar` の統合モデルに変換する。
- IO ディスパッチャーで実行し、エラーは `Result` 型でラップして上位に伝える。

### MsalAuthManager

- MSAL の `IMultipleAccountPublicClientApplication` をラップする。
- サインイン・サインアウト・サイレントトークン取得のインターフェースを提供する。
- `Application` スコープの singleton として DI で管理する（`Context` が必要なため `AndroidViewModel` に注意）。

### CalendarRepository（変更点）

- `OutlookCalendarDataSource` を既存の `CalendarDataSource` と並列に呼び出す。
- 両ソースの結果を時刻順にマージして `Flow` で流す。
- Room キャッシュには `source` フィールドを含めて保存し、ソース別に同期管理する。

### 認証 UI 状態

`WeeklyCalendarViewModel` の UI 状態に Outlook 認証状態を追加する。

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `outlookSignInState` | OutlookSignInState（sealed） | `NotSignedIn` / `Loading` / `SignedIn(email)` / `Error(message)` |

---

## ナビゲーション統合

### アカウント管理画面

`AppDestinations.PROFILE` の画面を「アカウント管理画面」として実装する。

- サインイン済みの場合: アカウントのメールアドレスとサインアウトボタンを表示
- 未サインインの場合: 「Outlook でサインイン」ボタンを表示

### カレンダー画面との連携

`WeeklyCalendarScreen` のカレンダー一覧に Outlook カレンダーをローカルカレンダーと並べて表示する。各カレンダー行にソースアイコン（Outlook ロゴまたはデバイスアイコン）を付与して区別する。

---

## 依存関係

以下を `libs.versions.toml` と `app/build.gradle.kts` に追加する。

| ライブラリ | 用途 |
|-----------|------|
| `com.microsoft.identity.client:msal` | OAuth 2.0 認証・トークン管理 |
| `com.microsoft.graph:microsoft-graph` | Graph API クライアント（任意; 直接 HTTP でも可） |
| `com.squareup.retrofit2:retrofit` | Graph API を直接呼ぶ場合の HTTP クライアント |
| `com.squareup.moshi` または `kotlinx.serialization` | JSON デシリアライズ |

Microsoft Graph SDK は依存が重くなるため、`retrofit` + `kotlinx.serialization` で直接呼ぶ構成も検討する。

---

## セキュリティ考慮事項

- アクセストークンはメモリ内または MSAL の暗号化キャッシュにのみ保存し、独自ストレージに保存しない。
- アプリケーション ID（クライアント ID）は `strings.xml` に記述し、シークレット（クライアントシークレット）は Android アプリには含めない（公開クライアントのため不要）。
- `READ` 権限のみ要求し、書き込み権限は要求しない。

---

## 実装ステップ

1. Azure AD アプリ登録・クライアント ID 取得
2. MSAL 依存関係追加・`msal_config.json` 設定
3. `MsalAuthManager` 実装（サインイン / サイレントトークン取得 / サインアウト）
4. `OutlookCalendarDataSource` 実装（カレンダー一覧・イベント取得・モデル変換）
5. 統合データモデルへの `source` / `outlookEventId` フィールド追加
6. `CalendarRepository` に Outlook ソースを追加・マージロジック実装
7. Room スキーマ更新（`source` カラム追加、マイグレーション）
8. `WeeklyCalendarViewModel` に `outlookSignInState` 追加
9. アカウント管理画面（`ProfileScreen`）実装
10. `WeeklyCalendarScreen` のカレンダー一覧 UI にソースアイコン追加