package com.coiglesia.app.notificaciones

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import com.coiglesia.app.distritos.DistritosService

class NotificacionesActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private var token = ""
    private var distritos =
        emptyList<DistritosService.Distrito>()

    private var solicitudActual = 0

    private lateinit var selectorDistrito: Spinner
    private lateinit var actualizar: Button
    private lateinit var mensaje: TextView
    private lateinit var resumen: TextView
    private lateinit var lista: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        token = intent.getStringExtra(EXTRA_TOKEN) ?: ""

        if (token.isBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 16)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titulo = TextView(this).apply {
            text = "Notificaciones Distritales"
            textSize = 24f
            setTextColor(Color.rgb(25, 51, 86))
            setPadding(0, 0, 0, 16)
        }

        selectorDistrito = Spinner(this)

        actualizar = Button(this).apply {
            text = "Actualizar notificaciones"
            isAllCaps = false
            isEnabled = false
        }

        mensaje = TextView(this).apply {
            text = "Cargando distritos..."
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }

        resumen = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(25, 51, 86))
            setPadding(0, 8, 0, 12)
        }

        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val volver = Button(this).apply {
            text = "Volver al menú"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        contenido.addView(titulo)
        contenido.addView(etiqueta("Seleccionar distrito"))
        contenido.addView(selectorDistrito)
        contenido.addView(actualizar)
        contenido.addView(mensaje)
        contenido.addView(resumen)
        contenido.addView(lista)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(contenido)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(volver)
        setContentView(root)

        selectorDistrito.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    cargarNotificaciones()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        actualizar.setOnClickListener {
            cargarNotificaciones()
        }

        cargarDistritos()
    }

    private fun etiqueta(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            textSize = 17f
            setPadding(0, 12, 0, 6)
        }
    }

    private fun adaptador(
        opciones: List<String>
    ): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            opciones
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun cargarDistritos() {
        actualizar.isEnabled = false
        mensaje.text = "Cargando distritos..."

        Thread {
            val resultado = DistritosService.listar(token)

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                if (!resultado.correcto) {
                    mensaje.text = resultado.mensaje
                    return@runOnUiThread
                }

                distritos = resultado.datos.orEmpty()
                    .filter { it.estado == "activo" }

                if (distritos.isEmpty()) {
                    mensaje.text =
                        "No hay distritos activos disponibles."
                    return@runOnUiThread
                }

                selectorDistrito.adapter = adaptador(
                    distritos.map {
                        "${it.codigo} - ${it.nombre}"
                    }
                )
            }
        }.start()
    }

    private fun cargarNotificaciones() {
        val distrito = distritos.getOrNull(
            selectorDistrito.selectedItemPosition
        ) ?: return

        val solicitud = ++solicitudActual

        actualizar.isEnabled = false
        mensaje.text = "Consultando notificaciones..."
        resumen.text = ""
        lista.removeAllViews()

        Thread {
            val resultado = NotificacionesService.listar(
                token,
                distrito.id
            )

            runOnUiThread {
                if (
                    isFinishing ||
                    isDestroyed ||
                    solicitud != solicitudActual
                ) {
                    return@runOnUiThread
                }

                actualizar.isEnabled = true
                mensaje.text = resultado.mensaje

                if (!resultado.correcto) {
                    return@runOnUiThread
                }

                val notificaciones =
                    resultado.notificaciones

                val pendientes = notificaciones.count {
                    it.pendiente
                }

                resumen.text =
                    "Total: ${notificaciones.size} | " +
                    "Pendientes: $pendientes"

                if (notificaciones.isEmpty()) {
                    mensaje.text =
                        "Este distrito no tiene notificaciones."
                }

                notificaciones.forEach { aviso ->
                    val tarjeta = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 14, 16, 14)
                        setBackgroundColor(
                            if (aviso.pendiente) {
                                Color.rgb(231, 241, 255)
                            } else {
                                Color.WHITE
                            }
                        )
                    }

                    val encabezado = TextView(this).apply {
                        text =
                            if (aviso.pendiente) {
                                "● PENDIENTE"
                            } else {
                                "✓ LEÍDA"
                            }
                        textSize = 13f
                        setTextColor(
                            Color.rgb(25, 74, 130)
                        )
                    }

                    val titulo = TextView(this).apply {
                        text = aviso.titulo
                        textSize = 18f
                        setTextColor(
                            Color.rgb(25, 51, 86)
                        )
                        setPadding(0, 8, 0, 6)
                    }

                    val descripcion = TextView(this).apply {
                        text = aviso.mensaje
                        textSize = 15f
                    }

                    val fecha = TextView(this).apply {
                        text =
                            "Fecha: ${aviso.fechaCreacion}"
                        textSize = 12f
                        setPadding(0, 8, 0, 0)
                    }

                    tarjeta.addView(encabezado)
                    tarjeta.addView(titulo)
                    tarjeta.addView(descripcion)
                    tarjeta.addView(fecha)

                    if (aviso.pendiente) {
                        val botonLeida = Button(this).apply {
                            text = "Marcar como leída"
                            isAllCaps = false

                            setOnClickListener {
                                isEnabled = false
                                text = "Actualizando..."

                                Thread {
                                    val resultado =
                                        NotificacionesService.marcarLeida(
                                            token,
                                            aviso.id
                                        )

                                    runOnUiThread {
                                        if (isFinishing || isDestroyed) {
                                            return@runOnUiThread
                                        }

                                        if (resultado.correcto) {
                                            cargarNotificaciones()
                                        } else {
                                            isEnabled = true
                                            text = "Marcar como leída"
                                            mensaje.text = resultado.mensaje
                                        }
                                    }
                                }.start()
                            }
                        }

                        tarjeta.addView(botonLeida)
                    }

                    val espacio = LinearLayout.LayoutParams(
                        -1,
                        -2
                    ).apply {
                        bottomMargin = 14
                    }

                    lista.addView(tarjeta, espacio)
                }
            }
        }.start()
    }
}
