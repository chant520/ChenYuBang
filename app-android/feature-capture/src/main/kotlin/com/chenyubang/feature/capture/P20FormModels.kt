package com.chenyubang.feature.capture

import androidx.compose.runtime.Composable
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.UserExampleSaver
import java.io.Serializable

/** 一条已勾选归属（id + 词，候选顺序；手动增补排末尾）。 */
data class IdiomRef(
    val id: String,
    val word: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal const val YEAR_MIN = 2000
internal const val YEAR_MAX = 2100
private const val YEAR_TEXT_LENGTH = 4

/** P20 表单字段；错因 / 标签仅标错可用并落库（规范 v1.7 §8.1）。 */
data class P20FormState(
    val answer: String? = null,
    val reason: String = "",
    val tags: List<String> = emptyList(),
    val tagDraft: String = "",
    val isMistake: Boolean = false,
    val examType: String? = null,
    val yearText: String = "",
    val saving: Boolean = false,
    val saveError: String? = null,
) {
    val year: Int?
        get() =
            yearText
                .takeIf { it.length == YEAR_TEXT_LENGTH }
                ?.toIntOrNull()
                ?.takeIf { it in YEAR_MIN..YEAR_MAX }
    val yearInvalid: Boolean get() = yearText.isNotEmpty() && year == null
}

/** 候选区局部状态（由 P20CandidateController 持有，与表单状态组合成页面状态）。 */
data class P20CandidateUiState(
    val reviewText: String = "",
    val candidates: List<MatchCandidate> = emptyList(),
    val checked: List<IdiomRef> = emptyList(),
    val poolSize: Int = 0,
    val deleteMode: Boolean = false,
    val showManualPicker: Boolean = false,
    val info: String? = null,
    val candidateHint: String? = null,
    val loading: Boolean = true,
)

/**
 * P20 页面状态。checked 是有序的已勾选归属：候选按池顺序，手动增补排末尾；≥1 才可保存。
 * candidates 是展示窗口，窗口外的已勾选由「已选归属」保留撤销入口。
 */
data class P20CaptureState(
    val reviewText: String = "",
    val candidates: List<MatchCandidate> = emptyList(),
    val checked: List<IdiomRef> = emptyList(),
    val poolSize: Int = 0,
    val deleteMode: Boolean = false,
    val showManualPicker: Boolean = false,
    val info: String? = null,
    val candidateHint: String? = null,
    val loading: Boolean = true,
    val form: P20FormState = P20FormState(),
) {
    val empty: Boolean get() = !loading && candidates.isEmpty()
    val canCycle: Boolean get() = poolSize > P20Matcher.WINDOW_SIZE
    val canAdd: Boolean get() = candidates.size < P20Matcher.MAX_CANDIDATES
    val checkedIds: Set<String> get() = checked.map { it.id }.toSet()
    val canSave: Boolean get() = !loading && checked.isNotEmpty() && !form.saving && !form.yearInvalid
    val showsCheckedChips: Boolean
        get() = checked.size > 1 || checked.any { ref -> candidates.none { it.id == ref.id } }
}

/** P20 页面依赖打包，避免 Composable 必填参数超过 detekt 阈值。 */
data class P20FormDeps(
    val database: AppDatabase,
    val saver: UserExampleSaver,
    val imagePath: String,
    val manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit = { _, _ -> },
)

/** P20 事件集合（展平为一个对象，避免 Composable 参数过多）。 */
data class P20CaptureActions(
    val onEdit: () -> Unit = {},
    val onSelect: (String) -> Unit = { _ -> },
    val onCycle: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onToggleDelete: () -> Unit = {},
    val onDelete: (String) -> Unit = { _ -> },
    val onOpenManual: () -> Unit = {},
    val onDismissManual: () -> Unit = {},
    val onManualPick: (String) -> Unit = { _ -> },
    val onAnswer: (String) -> Unit = { _ -> },
    val onReason: (String) -> Unit = { _ -> },
    val onTagDraft: (String) -> Unit = { _ -> },
    val onAddTag: () -> Boolean = { false },
    val onRemoveTag: (String) -> Unit = { _ -> },
    val onToggleMistake: () -> Unit = {},
    val onExamType: (String?) -> Unit = { _ -> },
    val onYear: (String) -> Unit = { _ -> },
    val onSave: () -> Unit = {},
    val onDismissInfo: () -> Unit = {},
    val onDismissSaveError: () -> Unit = {},
    val manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit = { _, _ -> },
)
