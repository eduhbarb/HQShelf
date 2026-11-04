package com.hqshelf.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hqshelf.domain.model.Comic
import com.hqshelf.domain.usecase.AddComicUseCase
import com.hqshelf.domain.usecase.GetShelfUseCase
import com.hqshelf.domain.usecase.ReorderComicUseCase
import com.hqshelf.domain.usecase.RemoveComicUseCase
import kotlinx.coroutines.launch

enum class ShelfStyle {
    CARVALHO,
    BRANCO,
    PRETO
}

class ShelfViewModel(
    private val getShelfUseCase: GetShelfUseCase,
    private val addComicUseCase: AddComicUseCase,
    private val removeComicUseCase: RemoveComicUseCase,
    private val reorderComicUseCase: ReorderComicUseCase,
    private val context: Context,
    private val widgetUpdater: (Context) -> Unit
) : ViewModel() {

    private val _shelf = mutableStateOf<List<Comic>>(emptyList())
    val shelf: List<Comic> get() = _shelf.value

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("hqshelf_prefs", Context.MODE_PRIVATE)
    }

    private val _shelfStyle = mutableStateOf<ShelfStyle>(loadStyle())
    val shelfStyle: ShelfStyle get() = _shelfStyle.value

    private fun loadStyle(): ShelfStyle {
        return when (prefs.getString("shelf_style", null)) {
            "BRANCO" -> ShelfStyle.BRANCO
            "PRETO" -> ShelfStyle.PRETO
            else -> ShelfStyle.CARVALHO
        }
    }

    private fun invalidarWidget() {
        prefs.edit().putInt("widget_render_version", prefs.getInt("widget_render_version", 0) + 1).apply()
        widgetUpdater(context)
    }

    fun setShelfStyle(style: ShelfStyle) {
        _shelfStyle.value = style
        prefs.edit().putString("shelf_style", style.name).commit()
        invalidarWidget()
    }

    fun forcarAtualizacaoWidget() {
        invalidarWidget()
    }

    private val _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value

    fun load() = viewModelScope.launch {
        try {
            _shelf.value = getShelfUseCase()
            invalidarWidget()
        } catch (e: Exception) {
            _error.value = e.message ?: "Erro ao carregar a estante"
        }
    }

    fun addComic(uri: Uri, position: Int) = viewModelScope.launch {
        try {
            addComicUseCase(uri, position)
            _shelf.value = getShelfUseCase()
            invalidarWidget()
        } catch (e: Exception) {
            _error.value = e.message ?: "Erro ao adicionar HQ"
        }
    }

    fun remove(position: Int) = viewModelScope.launch {
        try {
            removeComicUseCase(position)
            _shelf.value = getShelfUseCase()
            invalidarWidget()
        } catch (e: Exception) {
            _error.value = e.message ?: "Erro ao remover HQ"
        }
    }

    fun reorder(fromSeq: Int, toSeq: Int) = viewModelScope.launch {
        try {
            val current = getShelfUseCase().toMutableList()
            if (fromSeq < 0 || fromSeq >= current.size || toSeq < 0 || toSeq >= current.size || fromSeq == toSeq) {
                return@launch
            }
            val moved = current.removeAt(fromSeq)
            current.add(toSeq, moved)
            val reordered = current.withIndex().map { (i, c) -> c.copy(position = i + 1) }
            reorderComicUseCase(reordered)
            _shelf.value = getShelfUseCase()
            invalidarWidget()
        } catch (e: Exception) {
            _error.value = e.message ?: "Erro ao reordenar a estante"
        }
    }

    fun clearError() {
        _error.value = null
    }
}
