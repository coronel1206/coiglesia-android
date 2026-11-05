package com.coiglesia.app.auth

import com.coiglesia.app.BuildConfig
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

        if (
            !baseUrl.startsWith("https://") ||
            apiKey.isBlank() ||
            accessToken.isBlank()
        ) {
            return AccessResult(
                false,
                "Configuracion o sesion incompleta"
            )
        }

        var connection: HttpURLConnection? = null

        return try {
            connection = URL(
                "$baseUrl/rest/v1/rpc/tiene_acceso_app"
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
                it.write(
                    "{}".toByteArray(Charsets.UTF_8)
                )
            }

            val status = connection.responseCode

            if (status !in 200..299) {
                AccessResult(
                    false,
                    "No fue posible verificar la autorizacion"
                )
            } else {
                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                when (response.trim()) {
                    "true" -> AccessResult(
                        true,
                        "Usuario autorizado"
                    )

                    "false" -> AccessResult(
                        false,
                        "Usuario sin cargo activo autorizado"
                    )

                    else -> AccessResult(
                        false,
                        "Respuesta de autorizacion invalida"
                    )
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
