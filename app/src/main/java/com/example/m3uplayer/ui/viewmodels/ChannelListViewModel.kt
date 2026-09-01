package com.example.m3uplayer.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3uplayer.data.local.entities.ChannelEntity
import com.example.m3uplayer.data.local.entities.ProfileEntity
import com.example.m3uplayer.data.repository.MainRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChannelListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MainRepository(application)

    private val _activeProfile = repository.getActiveProfile().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile

    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels: StateFlow<List<ChannelEntity>> = _channels

    init {
        viewModelScope.launch {
            _activeProfile.collect { profile ->
                profile?.let {
                    repository.getChannels(it.id).collect { list ->
                        _channels.value = list
                    }
                }
            }
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }
}
