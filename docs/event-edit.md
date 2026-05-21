# 予定編集機能 設計ドキュメント

## 概要

カレンダーイベントの新規作成・編集・削除を行う機能。Android CalendarContract への書き込みと、Outlook（Microsoft Graph API）への書き込みの両方に対応する。

---

## UI 設計

### 画面一覧

| 画面 | 起動元 | 用途 |
|------|--------|------|
| `EventEditScreen` | 空きスロットタップ / FAB / `EventDetailScreen` の編集ボタン | 新規作成・既存イベント編集 |
| 削除確認ダイアログ | `EventEditScreen` の削除ボタン / `EventDetailScreen` の削除ボタン | 削除確認 |

### EventEditScreen レイアウト

```
┌─────────────────────────────────┐
│ ← キャンセル    保存            │  ← トップバー（保存は入力完了時のみ有効）
├─────────────────────────────────┤
│ タイトル                        │
│ [________________________]      │
│                                 │
│ ☐ 終日                         │
│                                 │
│ 開始  [2025/05/19  09:00]       │
│ 終了  [2025/05/19  10:00]       │
│                                 │
│ カレンダー [仕事用 ▾]           │
│                                 │
│ 場所  [________________________]│
│                                 │
│ メモ  [________________________]│
│       [________________________]│
│                                 │
│ （編集時のみ）                  │
│ [   削除   ]                    │
└─────────────────────────────────┘
```

### インタラクション

| 操作 | 動作 |
|------|------|
| 空きスロットタップ（週ビュー） | タップした時刻を開始時刻として `EventEditScreen` を開く（新規作成） |
| FAB タップ（週ビュー） | 現在時刻の次の整数時刻を開始時刻として `EventEditScreen` を開く |
| イベントブロック長押し（週ビュー） | 削除確認ダイアログを表示 |
| `EventDetailScreen` の編集ボタン | 既存データを入力済みの状態で `EventEditScreen` を開く |
| `EventDetailScreen` の削除ボタン | 削除確認ダイアログを表示 |
| 終日トグル ON | 時刻入力を非表示にし、開始・終了を日付のみで入力 |
| 開始日時変更 | 終了日時が開始日時より前になる場合、終了日時を自動補正（開始 + 元の所要時間） |
| 保存ボタン | バリデーション後に保存処理を実行。成功後に前の画面へ戻る |
| 削除確認ダイアログ「削除」 | イベントを削除して週ビューへ戻る |

---

## 権限

### Android CalendarContract への書き込み

`WRITE_CALENDAR` 権限が必要。`READ_CALENDAR` と同時にリクエストする。

### Outlook（Graph API）への書き込み

MSAL で取得するスコープを `Calendars.Read` から `Calendars.ReadWrite` に変更する。

---

## データ層設計

### 書き込み先の振り分け

イベントの `source` フィールドで書き込み先を決定する。

| `source` | 書き込み先 |
|----------|-----------|
| `LOCAL` | Android CalendarContract |
| `OUTLOOK` | Microsoft Graph API |

新規作成時はユーザーが選択したカレンダーの `source` に従って書き込み先を決定する。

### Android CalendarContract への書き込み

`ContentResolver` を通じて `CalendarContract.Events` に対して `insert` / `update` / `delete` を実行する。

**新規作成時の必須フィールド:**

| フィールド | 内容 |
|-----------|------|
| `CALENDAR_ID` | ユーザーが選択したカレンダーの ID |
| `TITLE` | タイトル |
| `DTSTART` | 開始日時（UTC epoch ms） |
| `DTEND` | 終了日時（UTC epoch ms） |
| `EVENT_TIMEZONE` | 端末のデフォルトタイムゾーン |
| `ALL_DAY` | 終日フラグ（0 or 1） |

### Microsoft Graph API への書き込み

| 操作 | メソッド | パス |
|------|---------|------|
| 新規作成 | `POST` | `/me/calendars/{calendarId}/events` |
| 更新 | `PATCH` | `/me/events/{eventId}` |
| 削除 | `DELETE` | `/me/events/{eventId}` |

**リクエストボディの主要フィールド:**

| フィールド | 内容 |
|-----------|------|
| `subject` | タイトル |
| `start.dateTime` / `start.timeZone` | 開始日時・タイムゾーン |
| `end.dateTime` / `end.timeZone` | 終了日時・タイムゾーン |
| `isAllDay` | 終日フラグ |
| `body.content` | メモ（text 形式） |
| `location.displayName` | 場所 |

### Room キャッシュの同期

- 書き込み成功後、Room キャッシュも即時更新して UI を反映する。
- 削除成功後、Room キャッシュから該当エントリを削除する。
- API エラー時はキャッシュを変更せず、エラーを UI に通知する。

---

## バリデーション

| 条件 | エラー内容 |
|------|-----------|
| タイトルが空 | 保存ボタンを非活性にする |
| 終了日時 ≤ 開始日時（終日イベント以外） | 「終了日時は開始日時より後にしてください」をインライン表示 |
| カレンダー未選択 | 保存ボタンを非活性にする |

---

## アーキテクチャ

### レイヤー構成

```
UI (Compose)
  └── EventEditScreen
        └── EventEditViewModel
              └── CalendarRepository
                    ├── CalendarDataSource        ← Android ContentResolver 書き込み
                    └── OutlookCalendarDataSource ← Graph API 書き込み
```

### EventEditViewModel

- 編集対象イベント ID（新規作成時は null）を受け取る。
- 既存イベントの場合は Room キャッシュから初期値を取得して `UiState` に設定する。
- 各フィールドの変更イベント（`onTitleChange`・`onStartChange` など）を受け付けて状態を更新する。
- `save()` / `delete()` でリポジトリを呼び出し、結果を `SaveResult`（sealed class）として公開する。

### EventEdit UI 状態

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `title` | String | タイトル入力値 |
| `startDateTime` | LocalDateTime | 開始日時 |
| `endDateTime` | LocalDateTime | 終了日時 |
| `isAllDay` | Boolean | 終日フラグ |
| `selectedCalendarId` | Long / String | 選択中カレンダーの ID |
| `calendars` | List\<UserCalendar\> | 選択可能なカレンダー一覧 |
| `location` | String | 場所入力値 |
| `description` | String | メモ入力値 |
| `isSaving` | Boolean | 保存中フラグ |
| `titleError` | Boolean | タイトル未入力エラー |
| `dateRangeError` | Boolean | 日時範囲エラー |
| `saveResult` | SaveResult? | 保存・削除結果（成功時は画面終了を通知） |

### SaveResult（sealed class）

| 型 | 説明 |
|----|------|
| `Success` | 保存・削除成功。画面を閉じる。 |
| `Error(message: String)` | 保存・削除失敗。スナックバーで通知。 |

---

## ナビゲーション統合

- `WeeklyCalendarScreen` → `EventEditScreen`（新規作成）: 起動時の開始日時を引数で渡す。
- `EventDetailScreen` → `EventEditScreen`（編集）: 編集対象イベント ID を引数で渡す。
- `EventEditScreen` 保存・キャンセル後: `popBackStack()` で前の画面へ戻る。
- 削除後: `WeeklyCalendarScreen` まで `popBackStack()` する（詳細画面も閉じる）。

---

## 実装ステップ

1. `CalendarRepository` に `insertEvent` / `updateEvent` / `deleteEvent` メソッドを追加
2. `CalendarDataSource` に ContentResolver 書き込み処理を追加（`WRITE_CALENDAR` 権限確認含む）
3. `OutlookCalendarDataSource` に Graph API 書き込み処理を追加（スコープを `Calendars.ReadWrite` に変更）
4. `EventEditViewModel` 実装
5. `EventEditScreen` 実装（日時ピッカー・カレンダー選択ドロップダウン・バリデーション表示）
6. `WeeklyCalendarScreen` に FAB と空きスロットタップを追加
7. `EventDetailScreen` に編集・削除ボタンを追加
8. ナビゲーションに `EventEditScreen` のルートを追加
9. `AndroidManifest.xml` に `WRITE_CALENDAR` 権限を追加
10. MSAL スコープを `Calendars.ReadWrite` に更新