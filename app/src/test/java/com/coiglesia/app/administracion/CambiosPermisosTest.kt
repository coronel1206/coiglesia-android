package com.coiglesia.app.administracion

import org.junit.Assert.*
import org.junit.Test

class CambiosPermisosTest {
    @Test fun confirmaAltasYBajasSinIncluirPermisosConservados() {
        val r=CambiosPermisos.comparar(setOf("personas.consultar","membresias.crear"),setOf("personas.consultar","eclesiastica.consultar"))
        assertEquals(listOf("eclesiastica.consultar"),r.agregados)
        assertEquals(listOf("membresias.crear"),r.retirados)
        assertFalse(r.sinCambios)
    }
    @Test fun retirarTodosLosPermisosNoSeConfundeConNoCambiar() {
        val r=CambiosPermisos.comparar(setOf("personas.consultar"),emptySet())
        assertFalse(r.sinCambios);assertEquals(listOf("personas.consultar"),r.retirados)
    }
    @Test fun cambiarElOrdenNoProduceUnaModificacion() {
        assertTrue(CambiosPermisos.comparar(linkedSetOf("b","a"),linkedSetOf("a","b")).sinCambios)
    }
}
