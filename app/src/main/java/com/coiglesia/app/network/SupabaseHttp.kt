package com.coiglesia.app.network

import com.coiglesia.app.BuildConfig
import com.coiglesia.app.auth.SessionManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object SupabaseHttp {
    fun rpc(function: String, token: String, parameters: JSONObject): String {
        require(function.matches(Regex("[a-z_]+")))
        require(token.isNotBlank()) { "La sesión no está disponible. Inicie sesión nuevamente." }
        return request("/rest/v1/rpc/$function", parameters, SessionManager.tokenFor(token))
    }

    /** No reintenta POST: una respuesta perdida puede corresponder a una operación confirmada. */
    fun request(path: String, payload: JSONObject, bearer: String? = null): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(base.startsWith("https://") && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Configuración de Supabase incompleta"
        }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                throw IllegalStateException(when {
                    status == 401 -> "La sesión expiró o no es válida. Inicie sesión nuevamente."
                    status == 403 || error.optString("code") == "42501" -> "No tiene permiso para realizar esta operación."
                    status == 409 || error.optString("code") == "23505" -> "El registro ya existe o la persona ya tiene una membresía activa. Actualice la información."
                    error.optString("code") == "P0001" -> error.optString("message").take(300)
                    status >= 500 -> "El servidor no pudo completar la solicitud. Revise el historial antes de repetir."
                    else -> "No se pudo completar la solicitud (HTTP $status). Revise los datos e inténtelo nuevamente."
                })
            }
            return text
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("La conexión tardó demasiado. Compruebe el historial antes de repetir la operación.")
        } catch (_: java.io.IOException) {
            throw IllegalStateException("No se pudo confirmar la respuesta. Compruebe su conexión y actualice la información antes de repetir.")
        } finally { connection.disconnect() }
    }
}
