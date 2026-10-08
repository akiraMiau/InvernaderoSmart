package com.example.myapplication.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AppPreferences
import com.example.myapplication.data.AppTheme
import com.example.myapplication.data.ControlMode
import com.example.myapplication.data.GreenhouseState
import com.example.myapplication.data.SensorReading
import com.example.myapplication.data.TempUnit
import com.example.myapplication.data.UserProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun formatTemp(celsius: Float, unit: TempUnit): String {
    return if (unit == TempUnit.FAHRENHEIT) {
        val f = celsius * 1.8f + 32f
        "${String.format(Locale.US, "%.1f", f)} °F"
    } else {
        "${String.format(Locale.US, "%.1f", celsius)} °C"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGreenhouseScreen(
    viewModel: GreenhouseViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Escuchar mensajes de retroalimentación (Microinteracciones)
    LaunchedEffect(uiState.userSnackbarMessage) {
        uiState.userSnackbarMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.clearSnackbar()
        }
    }

    // Flujo principal: Splash (Carga) -> Onboarding (Tutorial) -> Autenticación (Login/SSO) -> Panel Principal
    when {
        uiState.isLoading -> {
            SplashScreen()
        }
        uiState.showTutorial -> {
            TutorialScreen(onFinish = {
                viewModel.dismissTutorial()
                viewModel.showSnackbar("¡Onboarding completado! Por favor inicia sesión.")
            })
        }
        !uiState.isLoggedIn -> {
            AuthScreen(viewModel = viewModel)
        }
        else -> {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                                    onCheckedChange = {
                                        viewModel.setSimulationMode(it)
                                        viewModel.showSnackbar(if (it) "Modo Simulación activado" else "Modo Bluetooth activado")
                                    }
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
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Pestaña Monitoreo Dashboard") },
                            label = { Text("Monitoreo") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Tune, contentDescription = "Pestaña Control de Escotilla") },
                            label = { Text("Control") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.ShowChart, contentDescription = "Pestaña Historial de Sensores") },
                            label = { Text("Historial") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Pestaña Conexión Bluetooth") },
                            label = { Text("Conexión") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Pestaña Perfil y Ajustes") },
                            label = { Text("Perfil") }
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
                        0 -> DashboardTab(
                            uiState = uiState,
                            onGoToConnection = { selectedTab = 3 }
                        )
                        1 -> ControlTab(
                            uiState = uiState,
                            onModeChange = {
                                viewModel.setControlMode(it)
                                viewModel.showSnackbar("Modo cambiado a ${if (it == ControlMode.AUTOMATIC) "Automático" else "Manual"}")
                            },
                            onHatchToggle = {
                                viewModel.toggleHatchManual(it)
                                viewModel.showSnackbar(if (it) "Comando: Abrir Escotilla enviado" else "Comando: Cerrar Escotilla enviado")
                            },
                            onThresholdChange = {
                                viewModel.updateTemperatureThreshold(it)
                                viewModel.showSnackbar("Umbral actualizado a ${formatTemp(it, uiState.appPreferences.tempUnit)}")
                            }
                        )
                        2 -> HistoryTab(
                            history = history,
                            unit = uiState.appPreferences.tempUnit,
                            onGenerateTest = { viewModel.generateTestReading() },
                            onClearHistory = { viewModel.clearHistory() }
                        )
                        3 -> ConnectionTab(
                            uiState = uiState,
                            pairedDevices = viewModel.getPairedBluetoothDevices(),
                            onConnectDevice = { device ->
                                viewModel.connectToBluetoothDevice(device)
                                viewModel.showSnackbar("Conectando con dispositivo Bluetooth...")
                            },
                            onDisconnect = {
                                viewModel.disconnectBluetooth()
                                viewModel.showSnackbar("Bluetooth desconectado")
                            },
                            onToggleSimulation = {
                                viewModel.setSimulationMode(it)
                                viewModel.showSnackbar(if (it) "Modo Simulación activado" else "Buscando dispositivos físicos")
                            },
                            onRetryConnection = {
                                viewModel.clearError()
                                viewModel.showSnackbar("Reintentando conexión Bluetooth...")
                            }
                        )
                        4 -> ProfileTab(
                            uiState = uiState,
                            onUpdateProfile = { name, email, phone ->
                                viewModel.updateUserProfile(name, email, phone)
                                viewModel.showSnackbar("Perfil actualizado correctamente")
                            },
                            onUpdatePreferences = { prefs ->
                                viewModel.updatePreferences(prefs)
                                viewModel.showSnackbar("Preferencias guardadas")
                            },
                            onReplayTutorial = {
                                viewModel.replayTutorial()
                            },
                            onLogout = {
                                viewModel.logout()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// BANNER OFFLINE / MODO DESCONECTADO
// ==========================================

@Composable
fun OfflineBanner(
    isSimulationMode: Boolean,
    isConnected: Boolean,
    onGoToConnection: () -> Unit
) {
    if (!isConnected) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFFFB74D))),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Sin conexión Bluetooth",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isSimulationMode) "Modo Simulación / Offline" else "Bluetooth Desconectado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            text = if (isSimulationMode) "Mostrando datos simulados en tiempo real." else "Mostrando últimos datos guardados.",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                TextButton(onClick = onGoToConnection) {
                    Text("Conectar", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                }
            }
        }
    }
}

// ==========================================
// PANTALLAS DE ESTADO (EMPTY STATE & ERROR STATE)
// ==========================================

@Composable
fun EmptyStateView(
    title: String,
    description: String,
    icon: ImageVector,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
fun ErrorStateView(
    title: String,
    description: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFEF5350))),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error de conexión",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFFD32F2F),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFFB71C1C),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reintentar")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reintentar Conexión")
            }
        }
    }
}

// ==========================================
// PANTALLA DE CARGA (SPLASH SCREEN)
// ==========================================

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splashTransition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = "Logo Invernadero Smart",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Invernadero Smart",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Control & Monitoreo Agrícola Automatizado",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Inicializando sensores y verificando sesión...",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

// ==========================================
// PANTALLAS DE AUTENTICACIÓN (LOGIN / REGISTRO / RECUPERACIÓN / SSO / BIOMETRÍA)
// ==========================================

enum class AuthMode {
    LOGIN,
    REGISTER,
    RECOVERY
}

@Composable
fun AuthScreen(viewModel: GreenhouseViewModel) {
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            when (authMode) {
                AuthMode.LOGIN -> LoginContent(
                    onLogin = { email, pass ->
                        if (viewModel.login(email, pass)) {
                            viewModel.showSnackbar("¡Bienvenido de nuevo!")
                        }
                    },
                    onSSO = { provider ->
                        viewModel.loginWithSSO(provider)
                    },
                    onBiometricLogin = {
                        viewModel.loginWithBiometrics()
                    },
                    onForgotPassword = { authMode = AuthMode.RECOVERY },
                    onNavigateToRegister = { authMode = AuthMode.REGISTER }
                )
                AuthMode.REGISTER -> RegisterContent(
                    onRegister = { name, email, pass ->
                        if (viewModel.register(name, email, pass)) {
                            viewModel.showSnackbar("¡Cuenta creada con éxito!")
                        }
                    },
                    onSSO = { provider ->
                        viewModel.loginWithSSO(provider)
                    },
                    onNavigateToLogin = { authMode = AuthMode.LOGIN }
                )
                AuthMode.RECOVERY -> RecoveryContent(
                    onBackToLogin = { authMode = AuthMode.LOGIN }
                )
            }
        }
    }
}

@Composable
fun LoginContent(
    onLogin: (String, String) -> Unit,
    onSSO: (String) -> Unit,
    onBiometricLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = "Ícono de la aplicación",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Iniciar Sesión",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Accede a tu panel de Invernadero Smart",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Correo Electrónico") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Ícono correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Ícono candado") },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Alternar visibilidad de contraseña"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onForgotPassword) {
                Text("¿Olvidaste tu contraseña?", fontSize = 13.sp)
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Por favor completa todos los campos"
                } else {
                    onLogin(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Botón de Autenticación Biométrica (Huella / FaceID)
        OutlinedButton(
            onClick = onBiometricLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = "Autenticación biométrica")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ingresar con Huella / Biometría", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                "  O continúa con  ",
                fontSize = 12.sp,
                color = Color.Gray
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botones Single Sign-On (SSO)
        SSOButton(
            text = "Continuar con Google",
            icon = Icons.Default.AccountCircle,
            containerColor = Color(0xFFF2F2F2),
            contentColor = Color(0xFF333333),
            onClick = { onSSO("Google SSO") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SSOButton(
            text = "Continuar con Apple",
            icon = Icons.Default.PhoneIphone,
            containerColor = Color(0xFF000000),
            contentColor = Color.White,
            onClick = { onSSO("Apple SSO") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("¿No tienes una cuenta?", fontSize = 14.sp, color = Color.Gray)
            TextButton(onClick = onNavigateToRegister) {
                Text("Regístrate", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RegisterContent(
    onRegister: (String, String, String) -> Unit,
    onSSO: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Ícono de registro",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Crear Cuenta",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Regístrate para monitorear tu invernadero",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMessage = null },
            label = { Text("Nombre Completo") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Correo Electrónico") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Contraseña") },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Ver contraseña"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text("Confirmar Contraseña") },
            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = "Confirmar contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    errorMessage = "Por favor completa todos los campos"
                } else if (password != confirmPassword) {
                    errorMessage = "Las contraseñas no coinciden"
                } else {
                    onRegister(name, email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Registrarse", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SSOButton(
            text = "Registrarse con Google",
            icon = Icons.Default.AccountCircle,
            containerColor = Color(0xFFF2F2F2),
            contentColor = Color(0xFF333333),
            onClick = { onSSO("Google SSO") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("¿Ya tienes una cuenta?", fontSize = 14.sp, color = Color.Gray)
            TextButton(onClick = onNavigateToLogin) {
                Text("Inicia sesión", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecoveryContent(
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF3E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = "Recuperar clave",
                modifier = Modifier.size(36.dp),
                tint = Color(0xFFE65100)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Recuperar Contraseña",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Flujo sin fricción: Ingresa tu correo registrado y te enviaremos las instrucciones de restablecimiento de inmediato.",
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isSubmitted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFF4CAF50))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Éxito al enviar",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¡Correo de Recuperación Enviado!",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Hemos enviado las instrucciones a: $email.\nRevisa tu bandeja de entrada o spam para restablecer tu contraseña.",
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onBackToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Volver a Iniciar Sesión")
            }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico Registrado") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Correo registrado") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        isSubmitted = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enviar Enlace de Recuperación", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onBackToLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver atrás")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Volver")
            }
        }
    }
}

@Composable
fun SSOButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(imageVector = icon, contentDescription = text)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// PANTALLA DE TUTORIAL / ONBOARDING (CON CONTEXTO DE PERMISOS CRÍTICOS)
// ==========================================

@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    val pages = listOf(
        TutorialPageData(
            "¡Bienvenido a Invernadero Smart!",
            "Gestiona y monitorea tu invernadero de forma inteligente desde la palma de tu mano.",
            Icons.Default.Eco,
            Color(0xFF4CAF50)
        ),
        TutorialPageData(
            "Monitoreo en Tiempo Real",
            "Observa la temperatura, humedad y luz. Recibirás alertas inmediatas si el calor supera el límite.",
            Icons.Default.Dashboard,
            Color(0xFF2196F3)
        ),
        TutorialPageData(
            "Control Total y Servomotores",
            "Cambia entre modo Automático (Arduino decide) o Manual (tú controlas la escotilla) y ajusta los umbrales.",
            Icons.Default.Tune,
            Color(0xFFFF9800)
        ),
        TutorialPageData(
            "Permisos Críticos de la App",
            "🔑 Para funcionar correctamente, requerimos acceso a Bluetooth (conectar al Arduino HC-05/ESP32) y Notificaciones (alertas críticas de emergencia).",
            Icons.Default.Security,
            Color(0xFF673AB7)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val page = pages[pageIndex]
                TutorialPageContent(page)
            }

            // Indicadores y Botones
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicadores de punto
                Row {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                    else Color.LightGray
                                )
                        )
                    }
                }

                // Botón Siguiente / Conceder Permisos
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (pagerState.currentPage < pages.size - 1) "Siguiente" else "¡Conceder Permisos e Ir a Iniciar Sesión!")
                }
            }
        }
    }
}

@Composable
fun TutorialPageContent(page: TutorialPageData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(page.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = page.title,
                modifier = Modifier.size(80.dp),
                tint = page.color
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 22.sp
        )
    }
}

data class TutorialPageData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

// ==========================================
// PESTAÑA DASHBOARD (MONITOREO)
// ==========================================

@Composable
fun DashboardTab(
    uiState: GreenhouseState,
    onGoToConnection: () -> Unit
) {
    val isTempHigh = uiState.temperature >= uiState.tempThreshold
    val tempUnit = uiState.appPreferences.tempUnit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner de Estado Offline / Simulación
        item {
            OfflineBanner(
                isSimulationMode = uiState.isSimulationMode,
                isConnected = uiState.isConnected,
                onGoToConnection = onGoToConnection
            )
        }

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
                            contentDescription = "Alerta de temperatura alta",
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
                                "La temperatura (${formatTemp(uiState.temperature, tempUnit)}) supera el umbral límite (${formatTemp(uiState.tempThreshold, tempUnit)}). Escotilla abierta por emergencia.",
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
                title = "Temperatura Ambiente",
                value = formatTemp(uiState.temperature, tempUnit),
                subtitle = "Umbral Configurado: ${formatTemp(uiState.tempThreshold, tempUnit)}",
                icon = Icons.Default.Thermostat,
                cardColor = if (isTempHigh) Color(0xFFFFE0B2) else Color(0xFFE8F5E9),
                iconColor = if (isTempHigh) Color(0xFFE65100) else Color(0xFF2E7D32)
            )
        }

        item {
            SensorCard(
                title = "Humedad Relativa",
                value = "${String.format(Locale.US, "%.1f", uiState.humidity)} %",
                subtitle = "Sensor DHT11 Digital",
                icon = Icons.Default.WaterDrop,
                cardColor = Color(0xFFE3F2FD),
                iconColor = Color(0xFF1565C0)
            )
        }

        item {
            SensorCard(
                title = "Nivel de Luz (LDR)",
                value = "${String.format(Locale.US, "%.0f", uiState.lightLevel)} %",
                subtitle = "Sensor Fotoresistencia Lux",
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
                    contentDescription = if (hatchOpen) "Escotilla abierta" else "Escotilla cerrada",
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

// ==========================================
// PESTAÑA CONTROL
// ==========================================

@Composable
fun ControlTab(
    uiState: GreenhouseState,
    onModeChange: (ControlMode) -> Unit,
    onHatchToggle: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit
) {
    val tempUnit = uiState.appPreferences.tempUnit
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
                            Icon(Icons.Default.DoorSliding, contentDescription = "Abrir escotilla")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir")
                        }

                        Button(
                            onClick = { onHatchToggle(false) },
                            enabled = uiState.controlMode == ControlMode.MANUAL && uiState.hatchOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Cerrar escotilla")
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
                        "Configuración Dinámica de Umbral",
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
                        "Umbral Actual: ${formatTemp(uiState.tempThreshold, tempUnit)}",
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

// ==========================================
// PESTAÑA HISTORIAL (CON EMPTY STATE)
// ==========================================

@Composable
fun HistoryTab(
    history: List<SensorReading>,
    unit: TempUnit,
    onGenerateTest: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Evolución Histórica",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    "Gráfico de Temperatura (${if (unit == TempUnit.FAHRENHEIT) "°F" else "°C"}) y Humedad (%)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (history.isNotEmpty()) {
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar historial")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            // ESTADO VACÍO (EMPTY STATE)
            EmptyStateView(
                title = "Aún no hay lecturas registradas",
                description = "El historial de sensores está vacío. Inicia la simulación o conecta tu módulo Arduino para comenzar a registrar datos.",
                icon = Icons.Default.ShowChart,
                actionText = "Generar Lectura de Prueba",
                onAction = onGenerateTest
            )
        } else {
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
                    Text("Temperatura", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    .height(200.dp)
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
                                "Temp: ${formatTemp(item.temperature, unit)}",
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
}

@Composable
fun LineChartCanvas(history: List<SensorReading>) {
    if (history.isEmpty()) return

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

// ==========================================
// PESTAÑA CONEXIÓN (CON ERROR STATE Y EMPTY STATE)
// ==========================================

@SuppressLint("MissingPermission")
@Composable
fun ConnectionTab(
    uiState: GreenhouseState,
    pairedDevices: List<BluetoothDevice>,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit,
    onRetryConnection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Conexión con Arduino / ESP32",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Estado de Error
        uiState.errorMessage?.let { error ->
            item {
                ErrorStateView(
                    title = "Fallo al Conectar con Bluetooth",
                    description = error,
                    onRetry = onRetryConnection
                )
            }
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Simulación Interna", fontWeight = FontWeight.Bold)
                        Text(
                            "Genera datos automáticos sin requerir hardware físico.",
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
                EmptyStateView(
                    title = "Sin dispositivos emparejados",
                    description = "No se encontraron módulos Bluetooth HC-05 o ESP32 emparejados. Asegúrate de encender el Bluetooth y vincular tu dispositivo desde los Ajustes del sistema.",
                    icon = Icons.Default.BluetoothSearching
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
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
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

// ==========================================
// PESTAÑA PERFIL Y AJUSTES
// ==========================================

@Composable
fun ProfileTab(
    uiState: GreenhouseState,
    onUpdateProfile: (String, String, String) -> Unit,
    onUpdatePreferences: (AppPreferences) -> Unit,
    onReplayTutorial: () -> Unit,
    onLogout: () -> Unit
) {
    val user = uiState.currentUser
    val prefs = uiState.appPreferences

    var nameInput by remember(user.name) { mutableStateOf(user.name) }
    var emailInput by remember(user.email) { mutableStateOf(user.email) }
    var phoneInput by remember(user.phone) { mutableStateOf(user.phone) }

    var showTermsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado de Usuario
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (user.name.isNotEmpty()) user.name.first().uppercase() else "U",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = user.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = user.email,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(user.authProvider, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Proveedor verificado",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // Sección: Información Personal
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Información Personal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Información Personal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nombre Completo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Correo Electrónico") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Teléfono de Contacto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onUpdateProfile(nameInput, emailInput, phoneInput)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar cambios")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Cambios")
                    }
                }
            }
        }

        // Sección: Preferencias de la App
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Preferencias",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Preferencias de la App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Tema de la Aplicación", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = prefs.theme == AppTheme.SYSTEM,
                            onClick = { onUpdatePreferences(prefs.copy(theme = AppTheme.SYSTEM)) },
                            label = { Text("Sistema") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = prefs.theme == AppTheme.LIGHT,
                            onClick = { onUpdatePreferences(prefs.copy(theme = AppTheme.LIGHT)) },
                            label = { Text("Claro") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = prefs.theme == AppTheme.DARK,
                            onClick = { onUpdatePreferences(prefs.copy(theme = AppTheme.DARK)) },
                            label = { Text("Oscuro") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Unidad de Temperatura", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = prefs.tempUnit == TempUnit.CELSIUS,
                            onClick = { onUpdatePreferences(prefs.copy(tempUnit = TempUnit.CELSIUS)) },
                            label = { Text("°C (Celsius)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = prefs.tempUnit == TempUnit.FAHRENHEIT,
                            onClick = { onUpdatePreferences(prefs.copy(tempUnit = TempUnit.FAHRENHEIT)) },
                            label = { Text("°F (Fahrenheit)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Sección: Notificaciones
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Ajustes notificaciones",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Ajustes de Notificaciones",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alertas de Temperatura Crítica", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Avisar cuando supere el umbral límite", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = prefs.notifyTemperatureAlerts,
                            onCheckedChange = {
                                onUpdatePreferences(prefs.copy(notifyTemperatureAlerts = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Apertura/Cierre de Escotilla", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Avisar cambio de estado de servomotor", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = prefs.notifyHatchAlerts,
                            onCheckedChange = {
                                onUpdatePreferences(prefs.copy(notifyHatchAlerts = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Avisos del Sistema", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Notificaciones generales de conexión", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = prefs.notifySystemAlerts,
                            onCheckedChange = {
                                onUpdatePreferences(prefs.copy(notifySystemAlerts = it))
                            }
                        )
                    }
                }
            }
        }

        // Sección: Términos de Servicio y Tutorial
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTermsDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Términos de servicio", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Términos de Servicio y Políticas de Privacidad",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = "Ver más", tint = Color.Gray)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReplayTutorial() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Help, contentDescription = "Reactivar tutorial", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Ver Tutorial de la App de Nuevo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = "Ver tutorial", tint = Color.Gray)
                    }
                }
            }
        }

        // Sección: Cerrar Sesión
        item {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Diálogo de Términos de Servicio
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Text("Términos de Servicio y Privacidad", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    item {
                        Text(
                            "Bienvenido a Invernadero Smart.\n\n" +
                                    "1. Aceptación de Términos: Al utilizar esta aplicación, aceptas gestionar de manera responsable el control automatizado y manual de las escotillas y actuadores conectadas a tu microcontrolador Arduino/ESP32.\n\n" +
                                    "2. Privacidad de Datos: Los datos de tus sensores (temperatura, humedad y luz) se procesan localmente en tu dispositivo y no se comparten con terceros.\n\n" +
                                    "3. Seguridad e Invernadero: Es responsabilidad del usuario verificar que los umbrales de temperatura y actuadores físicos tengan los mecanismos de seguridad adecuados para evitar sobrecalentamiento en tus cultivos.\n\n" +
                                    "4. Actualizaciones: Esta app se reserva el derecho de mejorar los protocolos Bluetooth y las capacidades de monitoreo en tiempo real.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Entendido y Aceptar")
                }
            }
        )
    }
}
