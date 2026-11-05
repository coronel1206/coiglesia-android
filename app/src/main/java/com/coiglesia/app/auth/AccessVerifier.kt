package com.coiglesia.app.auth

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object AccessVerifier {

    data class AccessResult(
        val authorized: Boolean,
        val message: String
    )

    fun verify(accessToken: String): AccessResult {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

        if (baseUrl.isBlank() || apiKey.isBlank() ||
            accessToken.isBlank()
        ) {
            return AccessResult(
                false,
                "Configuración o sesión incompleta"
            )
        }

        var connection: HttpURLConnection? = null

        return try {
            connection = URL(
                "$baseUrl/rest/v1/rpc/verificar_acceso_app"
            ).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )

            connection.outputStream.use {
                it.write("{}".toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode

            if (status !in 200..299) {
                AccessResult(
                    false,
                    "No fue posible verificar la autorización"
                )
            } else {
                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val rows = JSONArray(response)

                if (rows.length() != 1) {
                    AccessResult(false, "Respuesta de acceso inválida")
                } else {
                    val data = rows.getJSONObject(0)

                    val active = data.optBoolean(
                        "usuario_activo", false
                    )
                    val nationalAdmin = data.optBoolean(
                        "administrador_nacional", false
                    )

                    if (active && nationalAdmin) {
                        AccessResult(
                            true,
                            "Administrador Nacional autorizado"
                        )
                    } else {
                        AccessResult(
                            false,
                            "Usuario sin autorización nacional"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AccessResult(
                false,
                "Error al verificar permisos"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
