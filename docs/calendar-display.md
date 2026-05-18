# カレンダー表示機能 設計ドキュメント

## 概要

週単位でイベントを表示するウィークリービュー。Android カレンダー (`CalendarContract`) と連携し、デバイスに登録されたイベントを取得・表示する。

---

## UI 設計

### レイアウト構造

```
┌─────────────────────────────────────────┐
│  < 2025年5月 >                          │  ← 月ナビゲーションヘッダー
├────┬────┬────┬────┬────┬────┬────┬────┤
│    │ 月 │ 火 │ 水 │ 木 │ 金 │ 土 │ 日 │  ← 曜日ヘッダー行（日付付き）
│    │ 19 │ 20 │ 21 │ 22 │ 23 │ 24 │ 25 │
├────┼────┼────┼────┼────┼────┼────┼────┤
│ 00 │    │    │    │    │    │    │    │
│ 01 │    │    │    │    │    │    │    │
│ .. │    │ ██ │    │    │    │    │    │  ← イベントブロック
│ 09 │ ██ │    │    │    │    │    │    │
│ .. │    │    │    │    │    │    │    │
│ 23 │    │    │    │    │    │    │    │
└────┴────┴────┴────┴────┴────┴────┴────┘
```

- 縦軸: 00:00〜23:00 の時刻スロット（60 分単位）
- 横軸: 選択週の月〜日（7 列）
- イベントブロック: 開始・終了時刻に応じて高さと位置が決まる
- 終日イベント: 曜日ヘッダー直下に別帯で表示

### インタラクション

| 操作 | 動作 |
|------|------|
| 左右スワイプ | 前週 / 次週へ移動 |
| イベントタップ | イベント詳細画面へ遷移 |
| 今日ボタン | 現在週へスクロール |
| 現在時刻インジケーター | 当日の現在時刻に赤い横線を表示 |

---

## データ層設計

### 権限

`READ_CALENDAR` 実行時権限が必要。ユーザーが拒否した場合は空のカレンダーを表示し、権限説明 UI を表示する。

### Android CalendarContract 連携

`CalendarContract.Events` ContentProvider から週の範囲でクエリする。

**取得フィールド:**

| フィールド | 用途 |
|-----------|------|
| `_ID` | イベント識別子 |
| `TITLE` | 表示タイトル |
| `DTSTART` | 開始日時 (UTC epoch ms) |
| `DTEND` | 終了日時 (UTC epoch ms) |
| `ALL_DAY` | 終日イベントフラグ |
| `CALENDAR_ID` | 所属カレンダーの識別子 |
| `CALENDAR_COLOR` | カレンダー色 |
| `EVENT_TIMEZONE` | タイムゾーン |
| `DESCRIPTION` | 詳細説明 |
| `EVENT_LOCATION` | 場所 |

繰り返しイベントは `CalendarContract.Instances` テーブルを使って展開する。

### 複数カレンダー対応

`CalendarContract.Calendars` から全カレンダーのメタ情報（ID・名前・色・アカウント名）を取得する。各イベントは `CALENDAR_ID` で所属カレンダーと紐づけ、イベントブロックはカレンダー色で色分け表示する。

### データモデル

**カレンダー:**

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | Long | カレンダー識別子 |
| `name` | String | カレンダー名 |
| `color` | Int | 表示色 |
| `accountName` | String | 所属アカウント名 |

**イベント:**

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | Long | イベント識別子 |
| `calendarId` | Long | 所属カレンダー識別子 |
| `title` | String | タイトル |
| `startMs` | Long | 開始日時 (UTC epoch ms) |
| `endMs` | Long | 終了日時 (UTC epoch ms) |
| `allDay` | Boolean | 終日イベントか |
| `color` | Int | カレンダー色 |
| `timeZone` | String | タイムゾーン識別子 |
| `description` | String | 詳細説明 |
| `location` | String | 場所 |

### オフライン対応

Room データベースをローカルキャッシュとして利用する。

- 起動時にキャッシュから即座に表示し、バックグラウンドで ContentProvider と同期する
- 表示週が変わるたびに対象週のイベントをキャッシュへ書き込む
- カレンダーテーブルとイベントテーブルを別エンティティで管理し、外部キーで関連付ける
- ネットワーク非依存（ContentProvider がオフライン時も最後の同期結果を表示できる）

---

## アーキテクチャ

### レイヤー構成

```
UI (Compose)
  ├── WeeklyCalendarScreen ──→ EventDetailScreen
  │     └── WeeklyCalendarViewModel
  └── EventDetailScreen
        └── EventDetailViewModel
              └── CalendarRepository
                    ├── CalendarDataSource   ← ContentProvider アクセス
                    └── CalendarLocalSource  ← Room キャッシュ
```

- **`CalendarDataSource`**: ContentProvider クエリのみ担当。IO スレッドで実行。
- **`CalendarLocalSource`**: Room DAO を通じてキャッシュの読み書きを担当。
- **`CalendarRepository`**: 起動時はキャッシュから即時返却し、ContentProvider 取得後にキャッシュを更新。`Flow` で UI に流す。
- **`WeeklyCalendarViewModel`**: 表示週・権限状態を保持。イベントタップ時に詳細画面への遷移イベントを発行。
- **`EventDetailViewModel`**: イベント ID を受け取り、Room キャッシュから詳細データを取得して公開。
- **`WeeklyCalendarScreen`**: 週グリッドを描画。イベントタップで `EventDetailScreen` へ遷移。
- **`EventDetailScreen`**: タイトル・日時・場所・説明・カレンダー名を表示。

### WeeklyCalendar UI 状態

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `weekStart` | LocalDate | 表示週の月曜日 |
| `events` | List\<CalendarEvent\> | 取得済みイベント |
| `calendars` | List\<UserCalendar\> | 取得済みカレンダー一覧 |
| `permissionGranted` | Boolean | カレンダー権限の状態 |
| `isLoading` | Boolean | 読み込み中フラグ |

### EventDetail UI 状態

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `event` | CalendarEvent? | 表示対象イベント |
| `calendarName` | String | 所属カレンダー名 |
| `isLoading` | Boolean | 読み込み中フラグ |

---

## ナビゲーション統合

`AppDestinations.HOME` の画面コンテンツとして `WeeklyCalendarScreen` を配置する。現在の `Greeting` プレースホルダーを置き換える。

`EventDetailScreen` はトップレベルではなく、`WeeklyCalendarScreen` から push される子画面として扱う。