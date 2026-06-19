package com.example.deporteconnect.network

/**
 * Configuración de la URL base del backend.
 *
 * Importante:
 *  - "10.0.2.2" es el alias del emulador para acceder al "localhost" del PC.
 *    Es decir: cuando el emulador llama a 10.0.2.2:8080, llega a tu Spring Boot.
 *  - Si pruebas en CELULAR FÍSICO en la misma WiFi:
 *      reemplaza "10.0.2.2" por la IP de tu PC (ej: "192.168.1.45")
 *      Para saber tu IP: en Windows ejecuta "ipconfig", busca IPv4.
 *  - Si subes el backend a la nube (Railway, Render):
 *      reemplaza por "https://tu-app.railway.app/"
 */
object ApiConfig {

    const val BASE_URL = "http://10.0.2.2:8080/"

    // Cuando uses celular físico:
    // const val BASE_URL = "http://192.168.1.XX:8080/"

    // Timeouts en segundos
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}
