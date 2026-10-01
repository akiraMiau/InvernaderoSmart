package com.example.myapplication.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.ControlMode
import com.example.myapplication.data.GreenhouseState
import com.example.myapplication.data.SensorReading
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGreenhouseScreen(
    viewModel: GreenhouseViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Invernadero Smart",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            if (uiState.isSimulationMode) "Modo Simulación Activo"
                            else if (uiState.isConnected) "Conectado: ${uiState.connectedDeviceName ?: "Arduino"}"
                            else "Desconectado",
                            fontSize = 12.sp,
                            color = if (uiState.isConnected || uiState.isSimulationMode) Color(0xFF4CAF50) else Color(0xFFE53935)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (uiState.isSimulationMode) "Sim" else "BT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = uiState.isSimulationMode,
                            onCheckedChange = { viewModel.setSimulationMode(it) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Monitoreo") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Control") },
                    label = { Text("Control") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = "Historial") },
                    label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Conexión") },
                    label = { Text("Conexión") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(uiState = uiState)
                1 -> ControlTab(
                    uiState = uiState,
                    onModeChange = { viewModel.setControlMode(it) },
                    onHatchToggle = { viewModel.toggleHatchManual(it) },
                    onThresholdChange = { viewModel.updateTemperatureThreshold(it) }
                )
                2 -> HistoryTab(history = history)
                3 -> ConnectionTab(
                    uiState = uiState,
                    pairedDevices = viewModel.getPairedBluetoothDevices(),
                    onConnectDevice = { viewModel.connectToBluetoothDevice(it) },
                    onDisconnect = { viewModel.disconnectBluetooth() },
                    onToggleSimulation = { viewModel.setSimulationMode(it) }
                )
            }
        }
    }
}

@Composable
fun DashboardTab(uiState: GreenhouseState) {
    val isTempHigh = uiState.temperature >= uiState.tempThreshold

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Alerta Crítica en Tiempo Real
        item {
            AnimatedVisibility(visible = isTempHigh) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFEF5350))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "¡ALERTA DE TEMPERATURA CRÍTICA!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F),
                                fontSize = 14.sp
                            )
                            Text(
                                "La temperatura (${String.format(Locale.US, "%.1f", uiState.temperature)}°C) supera el umbral límite (${uiState.tempThreshold}°C). Escotilla abierta por emergencia.",
                                fontSize = 12.sp,
                                color = Color(0xFFB71C1C)
                            )
                        }
                    }
                }
            }
        }

        // Tarjeta de Estado de la Escotilla
        item {
            HatchStatusCard(hatchOpen = uiState.hatchOpen, controlMode = uiState.controlMode)
        }

        // Rejilla de Lecturas de Sensores
        item {
            Text(
                "Sensores en Tiempo Real",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            SensorCard(
                title = "Temperatura",
                value = "${String.format(Locale.US, "%.1f", uiState.temperature)} °C",
                subtitle = "Umbral: ${uiState.tempThreshold} °C",
                icon = Icons.Default.Thermostat,
                cardColor = if (isTempHigh) Color(0xFFFFE0B2) else Color(0xFFE8F5E9),
                iconColor = if (isTempHigh) Color(0xFFE65100) else Color(0xFF2E7D32)
            )
        }

        item {
            SensorCard(
                title = "Humedad Relativa",
                value = "${String.format(Locale.US, "%.1f", uiState.humidity)} %",
                subtitle = "Sensor DHT11",
                icon = Icons.Default.WaterDrop,
                cardColor = Color(0xFFE3F2FD),
                iconColor = Color(0xFF1565C0)
            )
        }

        item {
            SensorCard(
                title = "Nivel de Luz (LDR)",
                value = "${String.format(Locale.US, "%.0f", uiState.lightLevel)} %",
                subtitle = "Sensor Fotoresistencia",
                icon = Icons.Default.WbSunny,
                cardColor = Color(0xFFFFFDE7),
                iconColor = Color(0xFFF57F17)
            )
        }
    }
}

@Composable
fun HatchStatusCard(hatchOpen: Boolean, controlMode: ControlMode) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (hatchOpen) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
        animationSpec = tween(durationMillis = 500),
        label = "hatchBg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Estado de la Escotilla",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = if (hatchOpen) "ABIERTA" else "CERRADA",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (hatchOpen) Color(0xFFE65100) else Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Modo Actual: ${if (controlMode == ControlMode.AUTOMATIC) "Automático (Arduino)" else "Manual (App)"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray
                )
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (hatchOpen) Color(0xFFFFB74D) else Color(0xFF81C784)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hatchOpen) Icons.Default.DoorSliding else Icons.Default.Lock,
                    contentDescription = "Escotilla",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    cardColor: Color,
    iconColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(text = title, fontSize = 13.sp, color = Color.Gray)
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(text = subtitle, fontSize = 11.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun ControlTab(
    uiState: GreenhouseState,
    onModeChange: (ControlMode) -> Unit,
    onHatchToggle: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit
) {
    var thresholdInput by remember(uiState.tempThreshold) {
        mutableStateOf(String.format(Locale.US, "%.1f", uiState.tempThreshold))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "Control de Modos y Escotilla",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Selección de Modo Automático / Manual
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Modo de Operación",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = uiState.controlMode == ControlMode.AUTOMATIC,
                            onClick = { onModeChange(ControlMode.AUTOMATIC) },
                            label = { Text("🤖 Automático") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.controlMode == ControlMode.MANUAL,
                            onClick = { onModeChange(ControlMode.MANUAL) },
                            label = { Text("🎮 Manual") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.controlMode == ControlMode.AUTOMATIC)
                            "En Modo Automático, el Arduino decide la apertura según la temperatura del DHT11 y la luz del LDR."
                        else
                            "En Modo Manual, la App toma el control total e ignora la decisión automática de los sensores.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Control Manual de Escotilla
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.controlMode == ControlMode.MANUAL) Color(0xFFFFF8E1) else Color(0xFFF5F5F5)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Acción Manual de Escotilla (Servo)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (uiState.controlMode == ControlMode.MANUAL) Color.Black else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onHatchToggle(true) },
                            enabled = uiState.controlMode == ControlMode.MANUAL && !uiState.hatchOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DoorSliding, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir")
                        }

                        Button(
                            onClick = { onHatchToggle(false) },
                            enabled = uiState.controlMode == ControlMode.MANUAL && uiState.hatchOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cerrar")
                        }
                    }
                }
            }
        }

        // Configuración Dinámica de Umbrales (Ajustes)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Configuración Dinámica de Umbral (°C)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Define la temperatura a la cual el Arduino abrirá la escotilla automáticamente.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Umbral Actual: ${String.format(Locale.US, "%.1f", uiState.tempThreshold)} °C",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Slider(
                        value = uiState.tempThreshold,
                        onValueChange = { onThresholdChange(it) },
                        valueRange = 15.0f..45.0f,
                        steps = 60,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { thresholdInput = it },
                            label = { Text("Valor exacto (°C)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val value = thresholdInput.toFloatOrNull()
                                if (value != null) {
                                    onThresholdChange(value.coerceIn(15.0f, 45.0f))
                                }
                            }
                        ) {
                            Text("Aplicar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(history: List<SensorReading>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Evolución Histórica",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            "Gráfico lineal de Temperatura (°C) y Humedad (%)",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Leyenda
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFE53935), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Temperatura (°C)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF1E88E5), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Humedad (%)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Canvas Gráfico
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            LineChartCanvas(history = history)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Registros Recientes",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(history.reversed()) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sdf.format(Date(item.timestamp)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            "Temp: ${String.format(Locale.US, "%.1f", item.temperature)}°C",
                            fontSize = 13.sp,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Hum: ${String.format(Locale.US, "%.1f", item.humidity)}%",
                            fontSize = 13.sp,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LineChartCanvas(history: List<SensorReading>) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Esperando datos...", color = Color.Gray)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height

        val maxVal = 100f
        val minVal = 0f

        // Líneas de Rejilla de Fondo
        for (i in 0..4) {
            val y = height * (i / 4f)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (history.size < 2) return@Canvas

        val stepX = width / (history.size - 1)

        val tempPath = Path()
        val humPath = Path()

        history.forEachIndexed { index, reading ->
            val x = index * stepX

            // Normalización Y para Temp (0 - 100)
            val tempY = height - ((reading.temperature - minVal) / (maxVal - minVal) * height)
            val humY = height - ((reading.humidity - minVal) / (maxVal - minVal) * height)

            if (index == 0) {
                tempPath.moveTo(x, tempY)
                humPath.moveTo(x, humY)
            } else {
                tempPath.lineTo(x, tempY)
                humPath.lineTo(x, humY)
            }

            // Dibujar Puntos
            drawCircle(color = Color(0xFFE53935), radius = 3.dp.toPx(), center = Offset(x, tempY))
            drawCircle(color = Color(0xFF1E88E5), radius = 3.dp.toPx(), center = Offset(x, humY))
        }

        // Dibujar Trazos
        drawPath(
            path = tempPath,
            color = Color(0xFFE53935),
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawPath(
            path = humPath,
            color = Color(0xFF1E88E5),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectionTab(
    uiState: GreenhouseState,
    pairedDevices: List<BluetoothDevice>,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Conexión con Arduino",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Estado de Simulación / Conexión Real
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Modo Simulación Interna", fontWeight = FontWeight.Bold)
                        Text(
                            "Genera datos automáticos sin requerir Arduino físico.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = uiState.isSimulationMode,
                        onCheckedChange = { onToggleSimulation(it) }
                    )
                }
            }
        }

        // Lista de Dispositivos Emparejados
        item {
            Text(
                "Dispositivos Bluetooth Emparejados (HC-05 / ESP32)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (pairedDevices.isEmpty()) {
            item {
                Text(
                    "No se encontraron dispositivos emparejados. Empareja el módulo HC-05 o ESP32 desde los ajustes de Bluetooth de tu teléfono.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        } else {
            items(pairedDevices) { device ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                device.name ?: "Dispositivo sin nombre",
                                fontWeight = FontWeight.Bold
                            )
                            Text(device.address, fontSize = 12.sp, color = Color.Gray)
                        }

                        Button(
                            onClick = { onConnectDevice(device) },
                            enabled = !uiState.isConnected || uiState.connectedDeviceName != device.name
                        ) {
                            Text("Conectar")
                        }
                    }
                }
            }
        }

        if (uiState.isConnected) {
            item {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desconectar Bluetooth")
                }
            }
        }

        // Guía de Comandos Serie
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔌 Protocolo de Comunicación Arduino",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• Arduino envía a App: TEMP:28.5,HUM:60.0,LIGHT:75.0,HATCH:1\n" +
                                "• App envía a Arduino: MODE:AUTO | MODE:MANUAL | HATCH:OPEN | HATCH:CLOSE | THRESHOLD:30.0",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}
