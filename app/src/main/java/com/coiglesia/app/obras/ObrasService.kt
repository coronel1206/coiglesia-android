package com.coiglesia.app.obras

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ObrasService {

    data class Obra(
        val id: String,
        val congregacionOrigenId: String,
        val codigo: String,
        val nombre: String,
        val municipio: String,
        val departamento: String,
        val direccionReferencia: String,
        val estado: String
    )

    data class Resultado<T>(
        val correcto: Boolean,
        val datos: T? = null,
        val mensaje: String
    )

    private fun texto(
        item: JSONObject,
        campo: String
    ): String {
        return if (item.isNull(campo)) {
            ""
        } else {
            item.optString(campo, "")
        }
    }

    private fun solicitar(funcion: String, token: String, parametros: JSONObject): String =
        com.coiglesia.app.network.SupabaseHttp.rpc(funcion, token, parametros)

    fun listar(
        token: String,
        congregacionId: String
    ): Resultado<List<Obra>> {
        if (congregacionId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Seleccione una congregacion"
            )
        }

        return try {
            val parametros = JSONObject()
                .put(
                    "p_congregacion_id",
                    congregacionId
                )

            val respuesta = solicitar(
                "listar_obras_gestion_app",
                token,
                parametros
            )

            val arreglo = JSONArray(respuesta)
            val obras = mutableListOf<Obra>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                obras.add(
                    Obra(
                        id = item.getString("id"),
                        congregacionOrigenId =
                            item.getString(
                                "congregacion_origen_id"
                            ),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre"),
                        municipio = texto(
                            item, "municipio"
                        ),
                        departamento = texto(
                            item, "departamento"
                        ),
                        direccionReferencia = texto(
                            item, "direccion_referencia"
                        ),
                        estado = item.getString("estado")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = obras,
                mensaje = "Obras consultadas correctamente"
            )

        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al consultar obras"
            )
        }
    }

    fun crear(
        token: String,
        congregacionId: String,
        codigo: String,
        nombre: String,
        municipio: String,
        departamento: String,
        direccion: String
    ): Resultado<String> {

        if (
            congregacionId.isBlank() ||
            codigo.isBlank() ||
            nombre.isBlank()
        ) {
            return Resultado(
                correcto = false,
                mensaje =
                    "Congregacion, codigo y nombre son obligatorios"
            )
        }

        if (
            codigo.trim().length > 30 ||
            nombre.trim().length > 180
        ) {
            return Resultado(
                correcto = false,
                mensaje =
                    "Codigo o nombre demasiado largo"
            )
        }

        return try {
            val parametros = JSONObject()
                .put(
                    "p_congregacion_origen_id",
                    congregacionId
                )
                .put("p_codigo", codigo.trim())
                .put("p_nombre", nombre.trim())
                .put("p_municipio", municipio.trim())
                .put("p_departamento", departamento.trim())
                .put("p_direccion_referencia", direccion.trim())

            val respuesta = solicitar(
                "crear_obra_proyeccion",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje =
                    "Obra en proyeccion creada correctamente"
            )

        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al crear obra"
            )
        }
    }
}
