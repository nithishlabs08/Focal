package com.focal.android.media

import com.focal.android.data.model.CameraConflictState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecoveryManager(
    private val onRetryCamera: () -> Boolean,
    private val onReinitEncoder: () -> Boolean
) {
    private val _conflictState = MutableStateFlow(CameraConflictState.NORMAL)
    val conflictState: StateFlow<CameraConflictState> = _conflictState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var recoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun onCameraConflictDetected(reason: String) {
        _conflictState.value = CameraConflictState.UNAVAILABLE
        _errorMessage.value = "Camera temporarily unavailable: $reason. Retrying automatically..."

        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            var attempt = 0
            while (isActive && _conflictState.value != CameraConflictState.NORMAL && attempt < 10) {
                attempt++
                delay(2000L * attempt) // Exponential backoff
                _conflictState.value = CameraConflictState.RECOVERING

                val success = onRetryCamera()
                if (success) {
                    _conflictState.value = CameraConflictState.NORMAL
                    _errorMessage.value = null
                    break
                } else {
                    _conflictState.value = CameraConflictState.UNAVAILABLE
                }
            }
        }
    }

    fun onEncoderFailure(error: Throwable) {
        _errorMessage.value = "Hardware encoder failure: ${error.localizedMessage ?: "Unknown"}. Falling back..."
        scope.launch {
            delay(1000)
            onReinitEncoder()
        }
    }

    fun manualRetry(): Boolean {
        recoveryJob?.cancel()
        _conflictState.value = CameraConflictState.RECOVERING
        val success = onRetryCamera()
        if (success) {
            _conflictState.value = CameraConflictState.NORMAL
            _errorMessage.value = null
        } else {
            _conflictState.value = CameraConflictState.UNAVAILABLE
        }
        return success
    }

    fun clearState() {
        recoveryJob?.cancel()
        recoveryJob = null
        _conflictState.value = CameraConflictState.NORMAL
        _errorMessage.value = null
    }
}
