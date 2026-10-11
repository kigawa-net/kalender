package net.kigawa.kalender.model

/**
 * イベントテンプレート
 * よく使う予定の雛形を保存しておくためのモデル
 */
@kotlinx.serialization.Serializable
data class EventTemplate(
    val id: Long = 0L,
    val name: String,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val durationMinutes: Int = 60,
    val allDay: Boolean = false,
    /** 繰り返し設定の構造化表現 */
    val recurrence: RecurrenceRule? = null,
    /** カレンダー指定（0の場合はデフォルトカレンダーを使用） */
    val preferredCalendarId: Long = 0L,
    /** 表示色（ARGB Int値） */
    val color: Int? = null,
    /** 作成日時（ミリ秒） */
    val createdAt: Long = 0L,
    /** 更新日時（ミリ秒） */
    val updatedAt: Long = 0L,
) {
    /** 繰り返しルールのRRULE文字列（recurrenceから導出） */
    val recurrenceRule: String? get() = recurrence?.toRRule()

    /** テンプレートが有効かどうか（名前が空でない） */
    val isValid: Boolean get() = name.isNotBlank()

    companion object {
        /** 空のテンプレート作成用 */
        fun empty(): EventTemplate = EventTemplate(name = "")
    }
}
