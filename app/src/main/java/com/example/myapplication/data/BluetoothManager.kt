package com.example.myapplication.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random

class BluetoothConnectionManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectionJob: Job? = null
    private var simulationJob: Job? = null

    // Standard SPP UUID for HC-05 / ESP32 Bluetooth Serial
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00885F9B34FB")

    private val _incomingData = MutableStateFlow<String?>(null)
    val incomingData: StateFlow<String?> = _incomingData.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        disconnect()
        connectionJob = scope.launch {
            try {
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream

                _isConnected.value = true
                _connectedDeviceName.value = device.name ?: device.address

                listenForData()
            } catch (e: IOException) {
                e.printStackTrace()
                disconnect()
            }
        }
    }

    private fun listenForData() {
        val buffer = ByteArray(1024)
        while (scope.isActive && _isConnected.value) {
            try {
                val bytes = inputStream?.read(buffer) ?: break
                if (bytes > 0) {
                    val message = String(buffer, 0, bytes).trim()
                    _incomingData.value = message
                }
            } catch (e: IOException) {
                disconnect()
                break
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                outputStream?.write("$command\n".toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun startSimulation(onSimulationTick: (SensorReading, Boolean) -> Unit) {
        stopSimulation()
        var currentTemp = 24.0f
        var currentHum = 60.0f
        var currentLight = 70.0f
        var isHatchOpen = false

        simulationJob = scope.launch {
            while (isActive) {
                delay(3000) // Actualización cada 3 segundos
                // Variación aleatoria suave de simulación
                currentTemp += Random.nextFloat() * 1.2f - 0.5f
                currentTemp = currentTemp.coerceIn(18.0f, 42.0f)

                currentHum += Random.nextFloat() * 2.0f - 1.0f
                currentHum = currentHum.coerceIn(30.0f, 95.0f)

                currentLight += Random.nextFloat() * 4.0f - 2.0f
                currentLight = currentLight.coerceIn(10.0f, 100.0f)

                val reading = SensorReading(
                    temperature = currentTemp,
                    humidity = currentHum,
                    lightLevel = currentLight
                )
                onSimulationTick(reading, isHatchOpen)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    fun disconnect() {
        connectionJob?.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        _isConnected.value = false
        _connectedDeviceName.value = null
    }
}
