package com.coiglesia.app.administracion

/** Resumen determinista de la selección exacta, sin convertir permisos en comodines. */
data class CambiosPermisos(val agregados: List<String>, val retirados: List<String>) {
    val sinCambios: Boolean get() = agregados.isEmpty() && retirados.isEmpty()
    companion object {
        fun comparar(original: Set<String>, seleccionado: Set<String>) = CambiosPermisos(
            (seleccionado-original).sorted(), (original-seleccionado).sorted()
        )
    }
}
