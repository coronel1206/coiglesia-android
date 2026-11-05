package com.coiglesia.app.congregaciones

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CongregacionesService {

    data class Congregacion(
        val id: String,
        val distritoId: String,
        val codigo: String,
        val nombre: String,
        val municipio: String,
        val departamento: String,
        val estado: String
    )

    data class Resultado<T>(
        val correcto: Boolean,
        val datos: T? = null,
        val mensaje: String
    )

    private fun solicitar(
        funcion: String,
        token: String,
        parametros: JSONObject
    ): String {
        require(token.isNotBlank()) {
            "La sesion no esta disponible"
        }

        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

        require(baseUrl.startsWith("https://") && apiKey.isNotBlank()) {
            "Configuracion de Supabase incompleta"
        }

        val conexion = URL(
            "$baseUrl/rest/v1/rpc/$funcion"
        ).openConnection() as HttpURLConnection

        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 15000
            conexion.doOutput = true
            conexion.setRequestProperty(
                "Content-Type", "application/json"
            )
            conexion.setRequestProperty("apikey", apiKey)
            conexion.setRequestProperty(
                "Authorization", "Bearer $token"
            )

            conexion.outputStream.use {
                it.write(
                    parametros.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val status = conexion.responseCode
            val respuesta = (
                if (status in 200..299) conexion.inputStream
                else conexion.errorStream
            )?.bufferedReader()?.use {
                it.readText()
            } ?: ""

            if (status !in 200..299) {
                throw IllegalStateException(
                    when (status) {
                        401, 403 -> "Acceso no autorizado"
                        409 -> "El codigo de la congregacion ya existe"
                        else -> {
                            val detalle = try {
                                JSONObject(respuesta)
                                    .optString("message")
                            } catch (_: Exception) {
                                ""
                            }
                            when {
                                status >= 500 -> "Error del servidor"
                                detalle.contains(
                                    "autorizacion",
                                    ignoreCase = true
                                ) -> "Acceso no autorizado"
                                else -> "No fue posible completar la solicitud"
                            }
                        }
                    }
                )
            }

            return respuesta
        } finally {
            conexion.disconnect()
        }
    }

    fun listar(
        token: String,
        distritoId: String
    ): Resultado<List<Congregacion>> {
        return try {
            val parametros = JSONObject()
                .put("p_distrito_id", distritoId)

            val respuesta = solicitar(
                "listar_congregaciones_app",
                token,
                parametros
            )

            val arreglo = JSONArray(respuesta)
            val congregaciones = mutableListOf<Congregacion>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                congregaciones.add(
                    Congregacion(
                        id = item.getString("id"),
                        distritoId = item.getString("distrito_id"),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre"),
                        municipio = item.optString("municipio", "")
                            .takeUnless { it == "null" } ?: "",
                        departamento = item.optString("departamento", "")
                            .takeUnless { it == "null" } ?: "",
                        estado = item.getString("estado")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = congregaciones,
                mensaje = "Congregaciones consultadas"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error de conexion"
            )
        }
    }

    fun crear(
        token: String,
        distritoId: String,
        codigo: String,
        nombre: String,
        municipio: String,
        departamento: String
    ): Resultado<String> {
        if (distritoId.isBlank() ||
            codigo.isBlank() ||
            nombre.isBlank()
        ) {
            return Resultado(
                correcto = false,
                mensaje = "Distrito, codigo y nombre son obligatorios"
            )
        }

        if (codigo.trim().length > 30 ||
            nombre.trim().length > 180
        ) {
            return Resultado(
                correcto = false,
                mensaje = "Codigo o nombre demasiado largo"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_distrito_id", distritoId)
                .put("p_codigo", codigo.trim())
                .put("p_nombre", nombre.trim())
                .put("p_municipio", municipio.trim())
                .put("p_departamento", departamento.trim())

            val respuesta = solicitar(
                "crear_congregacion",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje = "Congregacion creada correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error al crear congregacion"
            )
        }
    }
}
