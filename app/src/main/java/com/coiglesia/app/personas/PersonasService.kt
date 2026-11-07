package com.coiglesia.app.personas

import com.coiglesia.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PersonasService {

    data class Persona(
        val id: String,
        val nombres: String,
        val apellidos: String,
        val tipoDocumento: String,
        val numeroDocumento: String,
        val telefono: String,
        val correoElectronico: String,
        val fechaNacimiento: String,
        val estado: String,
        val congregacionId: String,
        val congregacionNombre: String,
        val distritoId: String,
        val distritoNombre: String,
        val estadoMembresia: String
    )

    data class Resultado<T>(
        val correcto: Boolean,
        val datos: T? = null,
        val mensaje: String
    )

    private fun solicitar(funcion: String, token: String, parametros: JSONObject): String =
        com.coiglesia.app.network.SupabaseHttp.rpc(funcion, token, parametros)

    fun listar(token: String): Resultado<List<Persona>> {
        return try {
            val respuesta = solicitar(
                "listar_personas_resumen_app",
                token,
                JSONObject()
            )

            val arreglo = JSONArray(respuesta)
            val personas = mutableListOf<Persona>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                personas.add(
                    Persona(
                        id = item.getString("id"),
                        nombres = item.getString("nombres"),
                        apellidos = item.getString("apellidos"),
                        tipoDocumento = item.optString("tipo_documento", "")
                            .takeUnless { it == "null" } ?: "",
                        numeroDocumento = item.optString("numero_documento", "")
                            .takeUnless { it == "null" } ?: "",
                        telefono = item.optString("telefono", "")
                            .takeUnless { it == "null" } ?: "",
                        correoElectronico = item.optString("correo_electronico", "")
                            .takeUnless { it == "null" } ?: "",
                        fechaNacimiento = item.optString("fecha_nacimiento", "")
                            .takeUnless { it == "null" } ?: "",
                        estado = item.getString("estado"),
                        congregacionId = item.getString("congregacion_id"),
                        congregacionNombre = item.getString("congregacion_nombre"),
                        distritoId = item.getString("distrito_id"),
                        distritoNombre = item.getString("distrito_nombre"),
                        estadoMembresia = item.optString("estado_membresia", "no_disponible")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = personas,
                mensaje = "Personas consultadas correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error de conexion"
            )
        }
    }

    fun registrar(
        token: String,
        congregacionId: String,
        nombres: String,
        apellidos: String,
        tipoDocumento: String,
        numeroDocumento: String,
        telefono: String,
        correo: String,
        fechaNacimiento: String
    ): Resultado<String> {

        if (congregacionId.isBlank() ||
            nombres.isBlank() ||
            apellidos.isBlank()
        ) {
            return Resultado(
                correcto = false,
                mensaje = "Congregacion, nombres y apellidos son obligatorios"
            )
        }

        if (tipoDocumento.isBlank() != numeroDocumento.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Indique tipo y numero de documento"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_congregacion_id", congregacionId)
                .put("p_nombres", nombres.trim())
                .put("p_apellidos", apellidos.trim())
                .put("p_tipo_documento",
                    tipoDocumento.trim().ifBlank { null })
                .put("p_numero_documento",
                    numeroDocumento.trim().ifBlank { null })
                .put("p_telefono",
                    telefono.trim().ifBlank { null })
                .put("p_correo_electronico",
                    correo.trim().ifBlank { null })
                .put("p_fecha_nacimiento",
                    fechaNacimiento.trim().ifBlank { null })

            val respuesta = solicitar(
                "registrar_persona_app",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje = "Persona registrada correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error al registrar persona"
            )
        }
    }


    data class DistritoRegistro(
        val id: String,
        val codigo: String,
        val nombre: String
    )

    data class CongregacionRegistro(
        val id: String,
        val distritoId: String,
        val codigo: String,
        val nombre: String
    )

    fun listarDistritosRegistro(
        token: String
    ): Resultado<List<DistritoRegistro>> {
        return try {
            val respuesta = solicitar(
                "listar_distritos_personas_app",
                token,
                JSONObject()
            )

            val arreglo = JSONArray(respuesta)
            val distritos = mutableListOf<DistritoRegistro>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)
                distritos.add(
                    DistritoRegistro(
                        id = item.getString("id"),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre")
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
                mensaje = e.message ?: "Error al consultar distritos"
            )
        }
    }

    fun listarCongregacionesRegistro(
        token: String,
        distritoId: String
    ): Resultado<List<CongregacionRegistro>> {
        if (distritoId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Seleccione un distrito"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_distrito_id", distritoId)

            val respuesta = solicitar(
                "listar_congregaciones_personas_app",
                token,
                parametros
            )

            val arreglo = JSONArray(respuesta)
            val congregaciones =
                mutableListOf<CongregacionRegistro>()

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)
                congregaciones.add(
                    CongregacionRegistro(
                        id = item.getString("id"),
                        distritoId = item.getString("distrito_id"),
                        codigo = item.getString("codigo"),
                        nombre = item.getString("nombre")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = congregaciones,
                mensaje = "Congregaciones autorizadas consultadas"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al consultar congregaciones"
            )
        }
    }


    fun actualizar(
        token: String,
        personaId: String,
        nombres: String,
        apellidos: String,
        tipoDocumento: String,
        numeroDocumento: String,
        telefono: String,
        correo: String,
        fechaNacimiento: String
    ): Resultado<String> {

        if (personaId.isBlank() ||
            nombres.isBlank() ||
            apellidos.isBlank()
        ) {
            return Resultado(
                correcto = false,
                mensaje = "Persona, nombres y apellidos son obligatorios"
            )
        }

        if (tipoDocumento.isBlank() != numeroDocumento.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Indique tipo y numero de documento"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_persona_id", personaId)
                .put("p_nombres", nombres.trim())
                .put("p_apellidos", apellidos.trim())
                .put(
                    "p_tipo_documento",
                    tipoDocumento.trim().ifBlank { null }
                )
                .put(
                    "p_numero_documento",
                    numeroDocumento.trim().ifBlank { null }
                )
                .put(
                    "p_telefono",
                    telefono.trim().ifBlank { null }
                )
                .put(
                    "p_correo_electronico",
                    correo.trim().ifBlank { null }
                )
                .put(
                    "p_fecha_nacimiento",
                    fechaNacimiento.trim().ifBlank { null }
                )

            val respuesta = solicitar(
                "actualizar_persona_app",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje = "Datos personales actualizados correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message ?: "Error al actualizar persona"
            )
        }
    }


    data class Membresia(
        val id: String,
        val personaId: String,
        val congregacionId: String,
        val congregacionNombre: String,
        val distritoId: String,
        val distritoNombre: String,
        val fechaIngreso: String,
        val fechaSalida: String?,
        val estado: String,
        val observaciones: String?
    )

    fun listarMembresias(
        token: String,
        personaId: String
    ): Resultado<List<Membresia>> {
        if (personaId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Debe seleccionar una persona"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_persona_id", personaId)

            val respuesta = solicitar(
                "listar_membresias_persona_app",
                token,
                parametros
            )

            val arreglo = JSONArray(respuesta)
            val membresias = mutableListOf<Membresia>()

            fun JSONObject.textoOpcional(
                campo: String
            ): String? =
                if (isNull(campo)) null
                else optString(campo).ifBlank { null }

            for (i in 0 until arreglo.length()) {
                val item = arreglo.getJSONObject(i)

                membresias.add(
                    Membresia(
                        id = item.getString("membresia_id"),
                        personaId = item.getString("persona_id"),
                        congregacionId =
                            item.getString("congregacion_id"),
                        congregacionNombre =
                            item.getString("congregacion_nombre"),
                        distritoId = item.getString("distrito_id"),
                        distritoNombre =
                            item.getString("distrito_nombre"),
                        fechaIngreso =
                            item.getString("fecha_ingreso"),
                        fechaSalida =
                            item.textoOpcional("fecha_salida"),
                        estado = item.getString("estado"),
                        observaciones =
                            item.textoOpcional("observaciones")
                    )
                )
            }

            Resultado(
                correcto = true,
                datos = membresias,
                mensaje = "Membresias consultadas correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al consultar membresias"
            )
        }
    }


    fun finalizarMembresia(
        token: String,
        membresiaId: String,
        fechaSalida: String,
        observaciones: String
    ): Resultado<String> {

        if (membresiaId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Debe seleccionar una membresia"
            )
        }

        if (observaciones.length > 2000) {
            return Resultado(
                correcto = false,
                mensaje = "Las observaciones superan 2000 caracteres"
            )
        }

        if (fechaSalida.isNotBlank()) {
            try {
                val formato = java.text.SimpleDateFormat(
                    "yyyy-MM-dd",
                    java.util.Locale.US
                )
                formato.isLenient = false

                val fecha = formato.parse(fechaSalida)
                    ?: throw IllegalArgumentException()

                if (formato.format(fecha) != fechaSalida) {
                    throw IllegalArgumentException()
                }
            } catch (e: Exception) {
                return Resultado(
                    correcto = false,
                    mensaje = "Fecha invalida. Use AAAA-MM-DD"
                )
            }
        }

        return try {
            val parametros = JSONObject()
                .put("p_membresia_id", membresiaId)
                .put(
                    "p_fecha_salida",
                    fechaSalida.trim().ifBlank { null }
                )
                .put(
                    "p_observaciones",
                    observaciones.trim().ifBlank { null }
                )

            val respuesta = solicitar(
                "finalizar_membresia_app",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje = "Membresia finalizada correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al finalizar la membresia"
            )
        }
    }


    fun registrarMembresia(
        token: String,
        personaId: String,
        congregacionId: String,
        fechaIngreso: String,
        observaciones: String
    ): Resultado<String> {

        if (personaId.isBlank() || congregacionId.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "Seleccione una persona y una congregacion"
            )
        }

        ValidacionMembresia.validar(fechaIngreso, observaciones)?.let {
            return Resultado(false, mensaje = it)
        }
        if (fechaIngreso.isBlank()) {
            return Resultado(
                correcto = false,
                mensaje = "La fecha de ingreso es obligatoria"
            )
        }

        return try {
            val parametros = JSONObject()
                .put("p_persona_id", personaId)
                .put("p_congregacion_id", congregacionId)
                .put("p_fecha_ingreso", fechaIngreso.trim())
                .put(
                    "p_observaciones",
                    observaciones.trim().ifBlank { JSONObject.NULL }
                )

            val respuesta = solicitar(
                "registrar_membresia_app",
                token,
                parametros
            )

            Resultado(
                correcto = true,
                datos = respuesta.trim().trim('"'),
                mensaje = "Nueva membresia registrada correctamente"
            )
        } catch (e: Exception) {
            Resultado(
                correcto = false,
                mensaje = e.message
                    ?: "Error al registrar la nueva membresia"
            )
        }
    }

    fun trasladarPersona(
        token: String, personaId: String, congregacionDestinoId: String,
        fechaTraslado: String, observaciones: String, membresiaOrigenId: String
    ): Resultado<String> {
        if (personaId.isBlank() || congregacionDestinoId.isBlank()) {
            return Resultado(false, mensaje = "Seleccione persona y destino")
        }
        ValidacionMembresia.validar(fechaTraslado, observaciones)?.let {
            return Resultado(false, mensaje = it)
        }
        return try {
            // Una sola RPC: el servidor finaliza y registra dentro de la misma transacción.
            val respuesta = solicitar("trasladar_persona_app", token, JSONObject()
                .put("p_persona_id", personaId)
                .put("p_congregacion_destino_id", congregacionDestinoId)
                .put("p_membresia_origen_esperada_id", membresiaOrigenId)
                .put("p_fecha_traslado", fechaTraslado)
                .put("p_observaciones", observaciones.trim().ifBlank { JSONObject.NULL }))
            Resultado(true, respuesta.trim().trim('"'), "Traslado realizado correctamente")
        } catch (e: Exception) {
            Resultado(false, mensaje = e.message ?: "No se pudo confirmar el traslado")
        }
    }

    fun listarDestinosMembresia(token: String): Resultado<List<CongregacionRegistro>> = try {
        val items = JSONArray(solicitar("listar_destinos_membresia_app", token, JSONObject()))
        Resultado(true, (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            CongregacionRegistro(item.getString("id"), item.getString("distrito_id"),
                item.getString("codigo"), item.getString("nombre"))
        }, "Destinos consultados")
    } catch (e: Exception) {
        Resultado(false, mensaje = e.message ?: "No se pudieron consultar los destinos")
    }

    fun listarDistritosMembresia(token: String): Resultado<List<DistritoRegistro>> = try {
        val items = JSONArray(solicitar("listar_distritos_membresia_app", token, JSONObject()))
        Resultado(true, (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            DistritoRegistro(item.getString("id"), item.getString("codigo"), item.getString("nombre"))
        }, "Distritos consultados")
    } catch (e: Exception) {
        Resultado(false, mensaje = e.message ?: "No se pudieron consultar los distritos")
    }

}
