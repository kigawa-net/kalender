package net.kigawa.kalender.model

/**
 * 繰り返し予定の編集・削除対象。
 */
@kotlinx.serialization.Serializable
enum class RecurrenceEditScope {
    /** この予定（インスタンス）のみ */
    THIS_EVENT,

    /** この予定以降（この予定を含む） */
    THIS_AND_FOLLOWING,

    /** シリーズ全体 */
    ALL,
    ;

    /** UI表示用ラベル */
    val label: String
        get() = when (this) {
            THIS_EVENT -> "この予定のみ"
            THIS_AND_FOLLOWING -> "これ以降の予定"
            ALL -> "すべての予定"
        }
}
