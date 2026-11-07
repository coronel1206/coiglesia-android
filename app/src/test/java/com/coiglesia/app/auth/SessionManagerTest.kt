package com.coiglesia.app.auth

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionManagerTest {
    @After fun limpiar() { SessionManager.clear() }

    @Test fun cerrarSesionInvalidaElTokenAnterior() {
        SessionManager.install("sesion-a", "", 3600)
        assertEquals("sesion-a", SessionManager.tokenFor("sesion-a"))
        SessionManager.clear()
        assertThrows(IllegalStateException::class.java) { SessionManager.tokenFor("sesion-a") }
    }

    @Test fun cambiarCuentaRechazaLaSesionAnterior() {
        SessionManager.install("sesion-a", "", 3600)
        SessionManager.install("sesion-b", "", 3600)
        assertThrows(IllegalStateException::class.java) { SessionManager.tokenFor("sesion-a") }
        assertEquals("sesion-b", SessionManager.tokenFor("sesion-b"))
    }

    @Test fun sesionExpiradaSinRenovacionEsRechazada() {
        SessionManager.install("sesion-vencida", "", 0)
        assertThrows(IllegalStateException::class.java) { SessionManager.tokenFor("sesion-vencida") }
    }
}
