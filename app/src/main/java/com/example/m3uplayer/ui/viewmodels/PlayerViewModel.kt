package com.example.m3uplayer.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3uplayer.data.local.entities.EpgProgramEntity
import com.example.m3uplayer.data.repository.MainRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MainRepository(application)

    private val _currentProgram = MutableStateFlow<EpgProgramEntity?>(null)
    val currentProgram: StateFlow<EpgProgramEntity?> = _currentProgram

    fun loadEpg(channelId: String) {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        viewModelScope.launch {
            repository.getPrograms(channelId, today).collect { programs ->
                _currentProgram.value = programs.firstOrNull()
            }
        }
    }

    fun updateProgress(mediaId: String, name: String, position: Long) {
        viewModelScope.launch {
            repository.updateHistory(mediaId, name, position)
        }
    }
}
