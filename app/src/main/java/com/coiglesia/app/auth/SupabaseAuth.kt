package com.coiglesia.app.auth

import com.coiglesia.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SupabaseAuth {

    data class LoginResult(
        val success: Boolean,
        val accessToken: String? = null,
        val userId: String? = null,
        val message: String
    )

    fun login(email: String, password: String): LoginResult {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return LoginResult(false, message = "Supabase no configurado")
        }

        var connection: HttpURLConnection? = null

        return try {
            connection = URL("$baseUrl/auth/v1/token?grant_type=password")
                .openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", apiKey)

            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

            connection.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode

            val response = (
                if (status in 200..299) connection.inputStream
                else connection.errorStream
            )?.bufferedReader()?.use { it.readText() } ?: "{}"

            val json = JSONObject(response)

            if (status in 200..299) {
                val token = json.optString("access_token")
                val userId = json.optJSONObject("user")
                    ?.optString("id")

                if (token.isBlank() || userId.isNullOrBlank()) {
                    LoginResult(false, message = "Respuesta de acceso incompleta")
                } else {
                    LoginResult(
                        success = true,
                        accessToken = token,
                        userId = userId,
                        message = "Credenciales verificadas"
                    )
                }
            } else {
                LoginResult(
                    success = false,
                    message = "No fue posible iniciar sesión. Verifica tus credenciales."
                )
            }
        } catch (e: Exception) {
            LoginResult(
                success = false,
                message = "Error de conexión con Supabase"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
