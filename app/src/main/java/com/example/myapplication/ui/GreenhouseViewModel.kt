package com.example.myapplication.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.data.AppPreferences
import com.example.myapplication.data.BluetoothConnectionManager
import com.example.myapplication.data.ControlMode
import com.example.myapplication.data.GreenhouseState
import com.example.myapplication.data.NotificationHelper
import com.example.myapplication.data.SensorReading
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.UserPreferencesRepository
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.KeystoreBiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class GreenhouseViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationHelper = NotificationHelper(application)
    private val bluetoothManager = BluetoothConnectionManager(application)
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val authRepository = AuthRepository()
    private val biometricManager = KeystoreBiometricManager(application)

    private val _uiState = MutableStateFlow(GreenhouseState())
    val uiState: StateFlow<GreenhouseState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<SensorReading>>(emptyList())
    val history: StateFlow<List<SensorReading>> = _history.asStateFlow()

    private var hasAlertedTemp = false

    init {
        // Inicializar historial ficticio inicial
        val initialHistory = mutableListOf<SensorReading>()
        val now = System.currentTimeMillis()
        val hourMs = 3600000L
        for (i in 5 downTo 0) {
            initialHistory.add(
                SensorReading(
                    timestamp = now - (i * hourMs),
                    temperature = 22.0f + (6 - i) * 1.1f,
                    humidity = 65.0f - (6 - i) * 1.5f,
                    lightLevel = 50.0f + (6 - i) * 5.0f
                )
            )
        }
        _history.value = initialHistory

        // Carga inicial sincronizada: Esperar tutorial y biometría antes de quitar la Splash Screen (isLoading = false)
        viewModelScope.launch {
            try {
                val showTutorial = userPreferencesRepository.showTutorialFlow.first()
                val currentUser = authRepository.currentUser

                if (currentUser != null) {
                    val profile = authRepository.getUserProfile()
                    val biometricEnabled = try {
                        userPreferencesRepository.getBiometricEnabledFlow(currentUser.uid).first()
                    } catch (e: Exception) {
                        false
                    }

                    _uiState.update {
                        it.copy(
                            showTutorial = showTutorial,
                            isLoggedIn = true,
                            currentUser = profile,
                            isBiometricEnabled = biometricEnabled,
                            isAppUnlocked = !biometricEnabled, // Bloqueado si biometría activa, desbloqueado si no
                            isLoading = false
                        )
                    }

                    // Escuchar cambios posteriores de autenticación Firebase
                    launch {
                        authRepository.authStateFlow.collect { firebaseUser ->
                            if (firebaseUser != null) {
                                val currentProfile = authRepository.getUserProfile()
                                _uiState.update { state ->
                                    state.copy(
                                        isLoggedIn = true,
                                        currentUser = currentProfile,
                                        authErrorMessage = null,
                                        authSuccessMessage = null
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoggedIn = false,
                                        currentUser = UserProfile(),
                                        isAppUnlocked = true,
                                        isBiometricEnabled = false,
                                        authErrorMessage = null,
                                        authSuccessMessage = null
                                    )
                                }
                            }
                        }
                    }

                    // Escuchar cambios de preferencias biométricas del usuario actual
                    launch {
                        userPreferencesRepository.getBiometricEnabledFlow(currentUser.uid).collect { enabled ->
                            _uiState.update { it.copy(isBiometricEnabled = enabled) }
                        }
                    }

                } else {
                    _uiState.update {
                        it.copy(
                            showTutorial = showTutorial,
                            isLoggedIn = false,
                            isAppUnlocked = true,
                            isLoading = false
                        )
                    }

                    // Escuchar cambios de autenticación posterior para usuarios nuevos/login
                    launch {
                        authRepository.authStateFlow.collect { firebaseUser ->
                            if (firebaseUser != null) {
                                val currentProfile = authRepository.getUserProfile()
                                val biometricEnabled = try {
                                    userPreferencesRepository.getBiometricEnabledFlow(firebaseUser.uid).first()
                                } catch (e: Exception) {
                                    false
                                }
                                _uiState.update {
                                    it.copy(
                                        isLoggedIn = true,
                                        currentUser = currentProfile,
                                        isBiometricEnabled = biometricEnabled,
                                        isAppUnlocked = !biometricEnabled,
                                        authErrorMessage = null,
                                        authSuccessMessage = null
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoggedIn = false,
                                        currentUser = UserProfile(),
                                        isAppUnlocked = true,
                                        isBiometricEnabled = false,
                                        authErrorMessage = null,
                                        authSuccessMessage = null
                                    )
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isAppUnlocked = true) }
            }
        }

        // Observar ciclo de vida de la app para bloquear al pasar a segundo plano si la biometría está activa
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    super.onStop(owner)
                    val state = _uiState.value
                    if (state.isLoggedIn && state.isBiometricEnabled) {
                        _uiState.update { it.copy(isAppUnlocked = false) }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Iniciar simulación por defecto
        if (_uiState.value.isSimulationMode) {
            startSimulationMode()
        }

        // Escuchar datos de Bluetooth
        viewModelScope.launch {
            bluetoothManager.incomingData.collect { data ->
                data?.let { parseIncomingBluetoothData(it) }
            }
        }

        viewModelScope.launch {
            bluetoothManager.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }

        viewModelScope.launch {
            bluetoothManager.connectedDeviceName.collect { deviceName ->
                _uiState.update { it.copy(connectedDeviceName = deviceName) }
            }
        }
    }

    private fun startSimulationMode() {
        bluetoothManager.startSimulation { reading, _ ->
            processNewReading(reading.temperature, reading.humidity, reading.lightLevel)
        }
    }

    private fun processNewReading(temp: Float, hum: Float, light: Float) {
        val currentState = _uiState.value

        // Decisión de escotilla en MODO AUTOMÁTICO
        var hatchStatus = currentState.hatchOpen
        if (currentState.controlMode == ControlMode.AUTOMATIC) {
            val shouldOpen = temp >= currentState.tempThreshold
            if (shouldOpen != hatchStatus) {
                hatchStatus = shouldOpen
                val reason = if (shouldOpen) "Temperatura alta (${String.format(Locale.US, "%.1f", temp)}°C >= ${currentState.tempThreshold}°C)" else "Temperatura dentro de rango normal"
                notificationHelper.sendHatchAlert(hatchStatus, reason)
            }
        }

        // Notificación de Alerta Crítica por Temperatura Alta
        if (temp >= currentState.tempThreshold) {
            if (!hasAlertedTemp) {
                notificationHelper.sendTemperatureAlert(temp, currentState.tempThreshold)
                hasAlertedTemp = true
            }
        } else {
            hasAlertedTemp = false
        }

        // Actualizar estado UI
        _uiState.update {
            it.copy(
                temperature = temp,
                humidity = hum,
                lightLevel = light,
                hatchOpen = hatchStatus
            )
        }

        // Agregar al historial
        val newReading = SensorReading(
            timestamp = System.currentTimeMillis(),
            temperature = temp,
            humidity = hum,
            lightLevel = light
        )
        _history.update { list ->
            val updated = list.toMutableList()
            updated.add(newReading)
            if (updated.size > 20) updated.removeAt(0)
            updated
        }
    }

    private fun parseIncomingBluetoothData(data: String) {
        try {
            val parts = data.split(",")
            var temp = _uiState.value.temperature
            var hum = _uiState.value.humidity
            var light = _uiState.value.lightLevel
            var hatch = _uiState.value.hatchOpen

            for (part in parts) {
                val kv = part.split(":")
                if (kv.size == 2) {
                    val key = kv[0].trim().uppercase()
                    val value = kv[1].trim()
                    when (key) {
                        "TEMP" -> temp = value.toFloatOrNull() ?: temp
                        "HUM" -> hum = value.toFloatOrNull() ?: hum
                        "LIGHT" -> light = value.toFloatOrNull() ?: light
                        "HATCH" -> hatch = (value == "1" || value.equals("OPEN", ignoreCase = true))
                    }
                }
            }

            _uiState.update { it.copy(hatchOpen = hatch) }
            processNewReading(temp, hum, light)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setControlMode(mode: ControlMode) {
        _uiState.update { it.copy(controlMode = mode) }
        val modeCmd = if (mode == ControlMode.AUTOMATIC) "MODE:AUTO" else "MODE:MANUAL"
        bluetoothManager.sendCommand(modeCmd)
    }

    fun toggleHatchManual(open: Boolean) {
        if (_uiState.value.controlMode == ControlMode.MANUAL) {
            _uiState.update { it.copy(hatchOpen = open) }
            val hatchCmd = if (open) "HATCH:OPEN" else "HATCH:CLOSE"
            bluetoothManager.sendCommand(hatchCmd)
            notificationHelper.sendHatchAlert(open, "Control Manual desde App Android")
        }
    }

    fun updateTemperatureThreshold(newThreshold: Float) {
        _uiState.update { it.copy(tempThreshold = newThreshold) }
        bluetoothManager.sendCommand("THRESHOLD:${String.format(Locale.US, "%.1f", newThreshold)}")
    }

    fun setSimulationMode(enabled: Boolean) {
        _uiState.update { it.copy(isSimulationMode = enabled) }
        if (enabled) {
            startSimulationMode()
        } else {
            bluetoothManager.stopSimulation()
        }
    }

    fun dismissTutorial() {
        _uiState.update { it.copy(showTutorial = false) }
        viewModelScope.launch {
            userPreferencesRepository.setTutorialCompleted()
        }
    }

    fun replayTutorial() {
        _uiState.update { it.copy(showTutorial = true) }
    }

    // --- Autenticación Firebase Real ---
    fun login(email: String, pass: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (email.isBlank() || pass.isBlank()) {
            val msg = "Por favor completa todos los campos."
            _uiState.update { it.copy(authErrorMessage = msg) }
            onError(msg)
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.login(email, pass)
            _uiState.update { it.copy(isAuthLoading = false) }
            result.onSuccess {
                _uiState.update { state -> state.copy(currentUser = authRepository.getUserProfile(), authErrorMessage = null) }
                onSuccess()
            }.onFailure { error ->
                val errMsg = error.message ?: "Error al iniciar sesión."
                _uiState.update { state -> state.copy(authErrorMessage = errMsg) }
                onError(errMsg)
            }
        }
    }

    fun register(name: String, email: String, pass: String, confirmPass: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (name.isBlank() || email.isBlank() || pass.isBlank() || confirmPass.isBlank()) {
            val msg = "Por favor completa todos los campos."
            _uiState.update { it.copy(authErrorMessage = msg) }
            onError(msg)
            return
        }
        if (pass != confirmPass) {
            val msg = "Las contraseñas no coinciden."
            _uiState.update { it.copy(authErrorMessage = msg) }
            onError(msg)
            return
        }
        if (pass.length < 6) {
            val msg = "La contraseña debe tener al menos 6 caracteres."
            _uiState.update { it.copy(authErrorMessage = msg) }
            onError(msg)
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.register(name, email, pass)
            _uiState.update { it.copy(isAuthLoading = false) }
            result.onSuccess {
                _uiState.update { state -> state.copy(currentUser = authRepository.getUserProfile(), authErrorMessage = null) }
                onSuccess()
            }.onFailure { error ->
                val errMsg = error.message ?: "Error al registrar la cuenta."
                _uiState.update { state -> state.copy(authErrorMessage = errMsg) }
                onError(errMsg)
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (String) -> Unit = {}) {
        if (email.isBlank()) {
            val msg = "Por favor ingresa tu correo electrónico."
            _uiState.update { it.copy(authErrorMessage = msg) }
            onResult(msg)
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null, authSuccessMessage = null) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            _uiState.update { it.copy(isAuthLoading = false) }
            val message = result.getOrDefault("Si existe una cuenta asociada a este correo, se enviarán las instrucciones para restablecer la contraseña.")
            _uiState.update { state -> state.copy(authSuccessMessage = message) }
            onResult(message)
        }
    }

    fun logout() {
        val user = authRepository.currentUser
        viewModelScope.launch {
            if (user != null) {
                userPreferencesRepository.setBiometricEnabled(user.uid, false)
            }
        }
        authRepository.signOut()
        biometricManager.deleteBiometricKey()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                currentUser = UserProfile(),
                isAppUnlocked = true,
                isBiometricEnabled = false,
                authErrorMessage = null,
                authSuccessMessage = null
            )
        }
        showSnackbar("Sesión cerrada correctamente.")
    }

    fun clearAuthMessages() {
        _uiState.update { it.copy(authErrorMessage = null, authSuccessMessage = null) }
    }

    fun lockApp() {
        if (_uiState.value.isBiometricEnabled) {
            _uiState.update { it.copy(isAppUnlocked = false) }
            showSnackbar("Aplicación bloqueada. Se requiere autenticación biométrica.")
        } else {
            showSnackbar("La biometría no está habilitada en tu perfil.")
        }
    }

    fun loginWithSSO(provider: String) {
        val name = if (provider.contains("Google", ignoreCase = true)) "Usuario Google" else "Usuario Apple"
        val email = if (provider.contains("Google", ignoreCase = true)) "google.user@invernaderosmart.com" else "apple.user@invernaderosmart.com"
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                currentUser = UserProfile(
                    name = name,
                    email = email,
                    phone = "+52 55 9876 5432",
                    authProvider = provider
                )
            )
        }
        showSnackbar("Sesión iniciada con $provider")
    }

    fun authenticateWithBiometrics(activity: FragmentActivity, onSuccess: () -> Unit = {}) {
        if (!biometricManager.isBiometricAvailable()) {
            showSnackbar("El dispositivo no soporta biometría o no hay huellas registradas.")
            return
        }
        biometricManager.authenticate(
            activity = activity,
            title = "Desbloquear Invernadero Smart",
            subtitle = "Confirma tu identidad para acceder a tu sesión",
            negativeButtonText = "Usar Contraseña",
            onSuccess = {
                _uiState.update { it.copy(isAppUnlocked = true) }
                showSnackbar("¡Aplicación desbloqueada con éxito!")
                onSuccess()
            },
            onError = { error ->
                showSnackbar("Error biométrico: $error")
            },
            onCancel = {
                showSnackbar("Autenticación biométrica cancelada")
            }
        )
    }

    fun toggleBiometricSetting(activity: FragmentActivity, enable: Boolean) {
        val user = authRepository.currentUser
        if (user == null) {
            showSnackbar("Debes iniciar sesión para configurar la biometría.")
            return
        }
        if (enable && !biometricManager.isBiometricAvailable()) {
            showSnackbar("No hay hardware biométrico disponible o configurado en este dispositivo.")
            return
        }

        if (enable) {
            biometricManager.authenticate(
                activity = activity,
                title = "Activar Autenticación Biométrica",
                subtitle = "Confirma tu huella para proteger tu cuenta",
                negativeButtonText = "Cancelar",
                onSuccess = {
                    viewModelScope.launch {
                        userPreferencesRepository.setBiometricEnabled(user.uid, true)
                        _uiState.update { it.copy(isBiometricEnabled = true, isAppUnlocked = true) }
                        showSnackbar("Autenticación biométrica activada correctamente.")
                    }
                },
                onError = { err ->
                    showSnackbar("No se pudo activar la biometría: $err")
                },
                onCancel = {}
            )
        } else {
            viewModelScope.launch {
                userPreferencesRepository.setBiometricEnabled(user.uid, false)
                _uiState.update { it.copy(isBiometricEnabled = false, isAppUnlocked = true) }
                biometricManager.deleteBiometricKey()
                showSnackbar("Autenticación biométrica desactivada.")
            }
        }
    }

    fun loginWithBiometrics() {
        showSnackbar("Por favor inicia sesión con correo y contraseña primero. La biometría se usa para desbloquear tu sesión existente.")
    }

    // --- Feedback & Mensajes Toast / Snackbar ---
    fun showSnackbar(message: String) {
        _uiState.update { it.copy(userSnackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(userSnackbarMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearHistory() {
        _history.value = emptyList()
        showSnackbar("Historial limpiado. Estado Vacío activado.")
    }

    fun generateTestReading() {
        processNewReading(25.0f, 62.0f, 80.0f)
        showSnackbar("Primera lectura registrada con éxito")
    }

    // --- Perfil y Ajustes ---
    fun updateUserProfile(name: String, email: String, phone: String) {
        _uiState.update {
            it.copy(
                currentUser = it.currentUser.copy(
                    name = name,
                    email = email,
                    phone = phone
                )
            )
        }
    }

    fun updatePreferences(newPreferences: AppPreferences) {
        _uiState.update {
            it.copy(appPreferences = newPreferences)
        }
    }

    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return bluetoothManager.getPairedDevices()
    }

    fun connectToBluetoothDevice(device: BluetoothDevice) {
        setSimulationMode(false)
        bluetoothManager.connectToDevice(device)
    }

    fun disconnectBluetooth() {
        bluetoothManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.disconnect()
        bluetoothManager.stopSimulation()
    }
}
