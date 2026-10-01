package com.example.myapplication.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BluetoothConnectionManager
import com.example.myapplication.data.ControlMode
import com.example.myapplication.data.GreenhouseState
import com.example.myapplication.data.NotificationHelper
import com.example.myapplication.data.SensorReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class GreenhouseViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationHelper = NotificationHelper(application)
    private val bluetoothManager = BluetoothConnectionManager(application)

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

        // Simular tiempo de carga (Splash Screen)
        viewModelScope.launch {
            delay(2500) // 2.5 segundos de carga
            _uiState.update { it.copy(isLoading = false) }
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
