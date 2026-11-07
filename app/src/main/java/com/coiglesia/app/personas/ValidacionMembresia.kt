package com.coiglesia.app.personas

import java.time.LocalDate
import java.time.format.DateTimeParseException

object ValidacionMembresia {
    fun validar(fecha: String, observaciones: String, ingresoOrigen: String? = null,
                hoy: LocalDate = LocalDate.now()): String? {
        if (observaciones.length > 2000) return "Las observaciones superan 2000 caracteres"
        val dia = try { LocalDate.parse(fecha) } catch (_: DateTimeParseException) {
            return "Fecha inválida. Use AAAA-MM-DD"
        }
        if (dia.toString() != fecha) return "Fecha inválida. Use AAAA-MM-DD"
        if (dia > hoy) return "No se permite una fecha futura"
        if (ingresoOrigen != null && dia < LocalDate.parse(ingresoOrigen)) {
            return "El traslado no puede ser anterior al ingreso"
        }
        return null
    }
}
