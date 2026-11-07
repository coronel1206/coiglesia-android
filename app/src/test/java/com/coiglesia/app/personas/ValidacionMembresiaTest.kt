package com.coiglesia.app.personas

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ValidacionMembresiaTest {
    private val hoy = LocalDate.of(2026, 11, 6)
    @Test fun permiteTrasladoEnFechaDeIngreso() {
        assertNull(ValidacionMembresia.validar("2026-11-06", "", "2026-11-06", hoy))
    }
    @Test fun rechazaFechasInvalidasYFuturas() {
        listOf("", "2026-02-30", "2026-11-07", "2026-1-6", "2026-11-06x").forEach {
            assertNotNull(it, ValidacionMembresia.validar(it, "", hoy = hoy))
        }
    }
    @Test fun rechazaSalidaAnteriorAlIngreso() {
        assertNotNull(ValidacionMembresia.validar("2026-11-05", "", "2026-11-06", hoy))
    }
    @Test fun limitaObservaciones() {
        assertNull(ValidacionMembresia.validar("2026-11-06", "x".repeat(2000), hoy = hoy))
        assertNotNull(ValidacionMembresia.validar("2026-11-06", "x".repeat(2001), hoy = hoy))
    }
}
