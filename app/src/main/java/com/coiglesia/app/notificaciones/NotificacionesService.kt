package com.coiglesia.app.notificaciones

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NotificacionesService {

    data class Notificacion(
        val id: String,
        val distritoId: String,
        val obraId: String,
        val tipo: String,
        val titulo: String,
        val mensaje: String,
        val fechaCreacion: String,
        val fechaLectura: String?
    ) {
        val pendiente: Boolean
            get() = fechaLectura == null
    }

    data class Resultado(
        val correcto: Boolean,
        val notificaciones: List<Notificacion> = emptyList(),
        val mensaje: String
    )

    fun listar(
        token: String,
        distritoId: String
    ): Resultado {

        if (token.isBlank() || distritoId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Sesion o distrito no disponible"
            )
        }

        var conexion: HttpURLConnection? = null

        return try {
            val baseUrl =
                BuildConfig.SUPABASE_URL.trimEnd('/')

            val apiKey =
                BuildConfig.SUPABASE_PUBLISHABLE_KEY

            require(
                baseUrl.startsWith("https://") &&
                apiKey.isNotBlank()
            ) {
                "Configuracion de Supabase incompleta"
            }

            val url = URL(
                "$baseUrl/rest/v1/rpc/" +
                "listar_notificaciones_distritales_app"
            )

            conexion = url.openConnection()
                as HttpURLConnection

            conexion.requestMethod = "POST"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 15000
            conexion.doOutput = true

            conexion.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conexion.setRequestProperty(
                "apikey",
                apiKey
            )

            conexion.setRequestProperty(
                "Authorization",
                "Bearer ${com.coiglesia.app.auth.SessionManager.tokenFor(token)}"
            )

            val parametros = JSONObject()
                .put("p_distrito_id", distritoId)

            conexion.outputStream.use {
                it.write(
                    parametros.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val status = conexion.responseCode

            val respuesta = (
                if (status in 200..299) {
                    conexion.inputStream
                } else {
                    conexion.errorStream
                }
            )?.bufferedReader()?.use {
                it.readText()
            } ?: ""

            if (status !in 200..299) {
                val error = try {
                    JSONObject(respuesta)
                } catch (_: Exception) {
                    JSONObject()
                }

                val codigo = error.optString("code")

                val mensaje = when {
                    status == 401 ||
                    status == 403 ||
                    codigo == "42501" ->
                        "No tiene autorizacion para consultar este distrito"

                    status >= 500 ->
                        "Error del servidor"

                    else ->
                        error.optString(
                            "message",
                            "No fue posible consultar notificaciones"
                        )
                }

                return Resultado(
                    correcto = false,
                    mensaje = mensaje
                )
            }

            val arreglo = JSONArray(respuesta)
            val elementos = mutableListOf<Notificacion>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                elementos.add(
                    Notificacion(
                        id = item.getString("id"),
                        distritoId = item.getString("distrito_id"),
                        obraId = item.optString("obra_id", ""),
                        tipo = item.getString("tipo"),
                        titulo = item.getString("titulo"),
                        mensaje = item.getString("mensaje"),
                        fechaCreacion =
                            item.getString("fecha_creacion"),
                        fechaLectura =
                            if (item.isNull("fecha_lectura")) {
                                null
                            } else {
                                item.getString("fecha_lectura")
                            }
                    )
                )
            }

            Resultado(
                correcto = true,
                notificaciones = elementos,
                mensaje = "Notificaciones consultadas correctamente"
            )

        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al consultar notificaciones"
            )
        } finally {
            conexion?.disconnect()
        }
    }

    fun marcarLeida(
        token: String,
        notificacionId: String
    ): Resultado {

        if (token.isBlank() || notificacionId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Sesion o notificacion no disponible"
            )
        }

        var conexion: HttpURLConnection? = null

        return try {
            val baseUrl =
                BuildConfig.SUPABASE_URL.trimEnd('/')

            val apiKey =
                BuildConfig.SUPABASE_PUBLISHABLE_KEY

            require(
                baseUrl.startsWith("https://") &&
                apiKey.isNotBlank()
            ) {
                "Configuracion de Supabase incompleta"
            }

            conexion = URL(
                "$baseUrl/rest/v1/rpc/marcar_notificacion_leida_app"
            ).openConnection() as HttpURLConnection

            conexion.requestMethod = "POST"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 15000
            conexion.doOutput = true

            conexion.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            conexion.setRequestProperty("apikey", apiKey)
            conexion.setRequestProperty(
                "Authorization",
                "Bearer ${com.coiglesia.app.auth.SessionManager.tokenFor(token)}"
            )

            val parametros = JSONObject()
                .put("p_notificacion_id", notificacionId)

            conexion.outputStream.use {
                it.write(
                    parametros.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val status = conexion.responseCode

            val respuesta = (
                if (status in 200..299) {
                    conexion.inputStream
                } else {
                    conexion.errorStream
                }
            )?.bufferedReader()?.use {
                it.readText()
            } ?: ""

            if (status !in 200..299) {
                val error = try {
                    JSONObject(respuesta)
                } catch (_: Exception) {
                    JSONObject()
                }

                val mensaje = if (
                    status == 401 ||
                    status == 403 ||
                    error.optString("code") == "42501"
                ) {
                    "No tiene autorizacion para esta notificacion"
                } else {
                    error.optString(
                        "message",
                        "No fue posible actualizar la notificacion"
                    )
                }

                Resultado(
                    correcto = false,
                    mensaje = mensaje
                )
            } else if (respuesta.trim() == "true") {
                Resultado(
                    correcto = true,
                    mensaje = "Notificacion marcada como leida"
                )
            } else {
                Resultado(
                    correcto = false,
                    mensaje = "Respuesta inesperada del servidor"
                )
            }

        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al actualizar notificacion"
            )
        } finally {
            conexion?.disconnect()
        }
    }

}
