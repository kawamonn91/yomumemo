package jp.yomumemo.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.FolderEntity
import jp.yomumemo.app.data.repo.FolderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderManagementViewModel(
    private val folders: FolderRepository,
) : ViewModel() {

    val uiState: StateFlow<List<FolderEntity>> =
        folders.observeAll().map { it }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { folders.create(name) }
    }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { folders.rename(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { folders.delete(id) }
    }
}
