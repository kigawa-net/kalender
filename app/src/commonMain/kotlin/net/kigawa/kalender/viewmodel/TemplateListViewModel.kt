package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.LocalEventTemplateStore
import net.kigawa.kalender.model.EventTemplate
import net.kigawa.kalender.model.RecurrenceRule
import net.kigawa.kalender.util.nowMs

data class TemplateListUiState(
    val isLoading: Boolean = true,
    val templates: List<EventTemplate> = emptyList(),
    val editingTemplate: EventTemplate? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * 省略可能な引数で「変更なし」と「明示的な null」を区別するためのラッパー。
 * `updateEditing` で `recurrence = null` を「繰り返しなしに変更」として
 * 扱えるようにするために使う。
 */
sealed interface Optional<out T> {
    /** 変更しない(現在の値を維持) */
    data object Unchanged : Optional<Nothing>

    /** 明示的な値(null を含みうる) */
    data class Present<T>(val value: T) : Optional<T>
}

/**
 * イベントテンプレートの一覧・編集を管理するViewModel。
 */
class TemplateListViewModel(
    private val templateStore: LocalEventTemplateStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateListUiState())
    val uiState: StateFlow<TemplateListUiState> = _uiState.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    val templates: StateFlow<List<EventTemplate>> = _refreshTrigger
        .flatMapLatest { templateStore.observeAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            templateStore.observeAll().collect { list ->
                _uiState.update { it.copy(isLoading = false, templates = list) }
            }
        }
    }

    /** 新規テンプレート作成開始 */
    fun startCreate() {
        _uiState.update { it.copy(editingTemplate = EventTemplate.empty()) }
    }

    /** 既存テンプレート編集開始 */
    fun startEdit(template: EventTemplate) {
        _uiState.update { it.copy(editingTemplate = template) }
    }

    /** テンプレートから予定作成のため、テンプレートを取得して編集画面に渡す */
    fun useTemplate(templateId: Long, onLoaded: (EventTemplate) -> Unit) {
        viewModelScope.launch {
            val template = templateStore.findById(templateId)
            if (template != null) onLoaded(template)
        }
    }

    /** 編集中テンプレートのフィールド更新 */
    fun updateEditing(
        name: String? = null,
        title: String? = null,
        description: String? = null,
        location: String? = null,
        durationMinutes: Int? = null,
        allDay: Boolean? = null,
        /** 繰り返し設定。明示的に null を渡して「繰り返しなし」へ変更できる */
        recurrence: Optional<RecurrenceRule?> = Optional.Unchanged,
        color: Int? = null,
    ) {
        _uiState.update { state ->
            val current = state.editingTemplate ?: return@update state
            state.copy(
                editingTemplate = current.copy(
                    name = name ?: current.name,
                    title = title ?: current.title,
                    description = description ?: current.description,
                    location = location ?: current.location,
                    durationMinutes = durationMinutes ?: current.durationMinutes,
                    allDay = allDay ?: current.allDay,
                    recurrence = when (recurrence) {
                        is Optional.Unchanged -> current.recurrence
                        is Optional.Present -> recurrence.value
                    },
                    color = color ?: current.color,
                ),
                error = null,
            )
        }
    }

    /** 編集中テンプレートを保存 */
    fun saveEditing() {
        val template = _uiState.value.editingTemplate ?: return
        if (template.name.isBlank()) {
            _uiState.update { it.copy(error = "テンプレート名を入力してください") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val withTimestamps = template.copy(updatedAt = nowMs())
                templateStore.upsert(withTimestamps)
                _uiState.update { it.copy(isSaving = false, editingTemplate = null) }
                _refreshTrigger.update { it + 1 }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "テンプレートの保存に失敗しました")
                }
            }
        }
    }

    /** 編集キャンセル */
    fun cancelEditing() {
        _uiState.update { it.copy(editingTemplate = null, error = null) }
    }

    /** テンプレート削除 */
    fun delete(templateId: Long) {
        viewModelScope.launch {
            try {
                templateStore.delete(templateId)
                _refreshTrigger.update { it + 1 }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "テンプレートの削除に失敗しました") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
