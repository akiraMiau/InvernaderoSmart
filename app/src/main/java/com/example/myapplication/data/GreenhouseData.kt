package com.example.myapplication.data

enum class ControlMode {
    AUTOMATIC,
    MANUAL
}

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

enum class TempUnit {
    CELSIUS,
    FAHRENHEIT
}

data class UserProfile(
    val name: String = "Usuario Invernadero",
    val email: String = "usuario@invernaderosmart.com",
    val phone: String = "+52 55 1234 5678",
    val photoUri: String? = null,
    val authProvider: String = "Correo/Contraseña"
)

data class AppPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val notifyTemperatureAlerts: Boolean = true,
    val notifyHatchAlerts: Boolean = true,
    val notifySystemAlerts: Boolean = true
)

data class SensorReading(
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Float,
    val humidity: Float,
    val lightLevel: Float
)

data class GreenhouseState(
    val temperature: Float = 24.5f,
    val humidity: Float = 60.0f,
    val lightLevel: Float = 75.0f, // % o Lux
    val hatchOpen: Boolean = false,
    val controlMode: ControlMode = ControlMode.AUTOMATIC,
    val tempThreshold: Float = 30.0f, // Umbral configurable
    val isConnected: Boolean = false,
    val isSimulationMode: Boolean = true, // Por defecto en modo simulación para pruebas iniciales
    val connectedDeviceName: String? = null,
    val lastEmergencyAlert: String? = null,
    val isLoading: Boolean = true,    // Pantalla de carga (Splash)
    val showTutorial: Boolean = true,  // Tutorial después de carga
    val isLoggedIn: Boolean = false,   // Autenticación después del tutorial
    val currentUser: UserProfile = UserProfile(),
    val appPreferences: AppPreferences = AppPreferences()
)

