package com.coiglesia.app.distritos

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DistritosService {

    data class Distrito(
        val id: String,
        val codigo: String,
        val nombre: String,
        val descripcion: String,
        val estado: String
    )

    data class Resultado<T>(
        val correcto: Boolean,
        val datos: T? = null,
        val mensaje: String
    )

    private fun solicitar(funcion: String, token: String, parametros: JSONObject): String =
        com.coiglesia.app.network.SupabaseHttp.rpc(funcion, token, parametros)

    fun listar(token: String): Resultado<List<Distrito>> = listarFuncion(token, "listar_distritos_app")

    fun listarParaCongregaciones(token: String): Resultado<List<Distrito>> =
        listarFuncion(token, "listar_distritos_congregaciones_app")

    fun listarParaObras(token: String): Resultado<List<Distrito>> =
        listarFuncion(token, "listar_distritos_obras_app")

    private fun listarFuncion(token: String, funcion: String): Resultado<List<Distrito>> {
        return try {
            val respuesta = solicitar(
                funcion,
                token,
                JSONObject()
            )

            val arreglo = JSONArray(respuesta)
            val distritos = mutableListOf<Distrito>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                distritos.add(
                    Distrito(
                        id = item.getString("id"),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre"),
                        descripcion = item.optString(
                            "descripcion", ""
                        ).takeUnless { it == "null" } ?: "",
                        estado = item.getString("estado")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = distritos,
                mensaje = "Distritos consultados"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error de conexion"
            )
        }
    }


    fun listarAutorizados(
        token: String
    ): Resultado<List<Distrito>> {
        return try {
            val respuesta = solicitar(
                "listar_distritos_autorizados_app",
                token,
                JSONObject()
            )

            val arreglo = JSONArray(respuesta)
            val distritos = mutableListOf<Distrito>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                distritos.add(
                    Distrito(
                        id = item.getString("id"),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre"),
                        descripcion = item.optString(
                            "descripcion", ""
                        ).takeUnless { it == "null" } ?: "",
                        estado = item.getString("estado")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = distritos,
                mensaje = "Distritos autorizados consultados"
            )

        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al consultar distritos autorizados"
            )
        }
    }

    fun crear(
        token: String,
        codigo: String,
        nombre: String,
        descripcion: String
    ): Resultado<String> {
        if (codigo.isBlank() || nombre.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Codigo y nombre son obligatorios"
            )
        }

        if (codigo.trim().length > 20 ||
            nombre.trim().length > 150
        ) {
            return Resultado(
                correcto = false,
                mensaje = "Codigo o nombre demasiado largo"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_codigo", codigo.trim())
                .put("p_nombre", nombre.trim())
                .put(
                    "p_descripcion",
                    descripcion.trim()
                )

            val respuesta = solicitar(
                "crear_distrito",
                token,
                parametros
            )

            val id = respuesta.trim().trim('"')

            Resultado(
                correcto = true,
                datos = id,
                mensaje = "Distrito creado correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error al crear distrito"
            )
        }
    }
}
