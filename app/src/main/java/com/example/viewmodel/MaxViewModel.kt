package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.AssistantState
import com.example.live.LiveSessionManager
import com.example.live.LogMessage
import com.example.service.MaxVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaxViewModel : ViewModel() {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _permissionsMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsMap: StateFlow<Map<String, Boolean>> = _permissionsMap.asStateFlow()

    private var fallbackManager: LiveSessionManager? = null

    val assistantState: StateFlow<AssistantState>
        get() = MaxVoiceService.instance?.liveSessionManager?.assistantState
            ?: fallbackManager?.assistantState
            ?: MutableStateFlow(AssistantState.IDLE)

    val sassyStatus: StateFlow<String>
        get() = MaxVoiceService.instance?.liveSessionManager?.sassyStatus
            ?: fallbackManager?.sassyStatus
            ?: MutableStateFlow("Tap orb to start MAX!")

    val audioLevel: StateFlow<Float>
        get() = MaxVoiceService.instance?.liveSessionManager?.audioLevel
            ?: fallbackManager?.audioLevel
            ?: MutableStateFlow(0f)

    val logHistory: StateFlow<List<LogMessage>>
        get() = MaxVoiceService.instance?.liveSessionManager?.logHistory
            ?: fallbackManager?.logHistory
            ?: MutableStateFlow(emptyList())

    fun initFallbackSessionIfNeeded(context: Context) {
        if (MaxVoiceService.instance == null && fallbackManager == null) {
            fallbackManager = LiveSessionManager(context.applicationContext, viewModelScope)
        }
        checkPermissions(context)
    }

    fun checkPermissions(context: Context) {
        val perms = mutableMapOf<String, Boolean>()
        val requiredList = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredList.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        for (p in requiredList) {
            perms[p] = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }
        _permissionsMap.value = perms
    }

    fun startService(context: Context) {
        val intent = Intent(context, MaxVoiceService::class.java).apply {
            action = MaxVoiceService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _isServiceRunning.value = true
    }

    fun stopService(context: Context) {
        val intent = Intent(context, MaxVoiceService::class.java).apply {
            action = MaxVoiceService.ACTION_STOP
        }
        context.startService(intent)
        _isServiceRunning.value = false
    }

    fun toggleOrbListening(context: Context) {
        val manager = MaxVoiceService.instance?.liveSessionManager ?: fallbackManager
        if (manager == null) {
            startService(context)
        } else {
            manager.toggleListeningState()
        }
    }

    fun sendTextCommand(text: String, context: Context) {
        val manager = MaxVoiceService.instance?.liveSessionManager ?: fallbackManager
        if (manager != null) {
            manager.processDirectTextCommand(text)
        } else {
            initFallbackSessionIfNeeded(context)
            fallbackManager?.processDirectTextCommand(text)
        }
    }
}
