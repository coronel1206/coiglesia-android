package com.coiglesia.app.personas

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class PersonasActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private lateinit var mensaje: TextView
    private lateinit var lista: LinearLayout
    private lateinit var actualizar: Button

    private var token = ""
    private var solicitudLista = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        token = intent.getStringExtra(EXTRA_TOKEN) ?: ""

        if (token.isBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val titulo = TextView(this).apply {
            text = "Personas y Membresías"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 51, 86))
        }

        actualizar = Button(this).apply {
            text = "Actualizar personas"
            isAllCaps = false
        }

        mensaje = TextView(this).apply {
            text = "Preparando consulta..."
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }

        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(lista)
        }

        val volver = Button(this).apply {
            text = "Volver al menú"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        root.addView(titulo)
        root.addView(actualizar)
        root.addView(mensaje)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(volver)

        setContentView(root)

        actualizar.setOnClickListener {
            cargarPersonas()
        }

        cargarPersonas()
    }

    private fun cargarPersonas() {
        val solicitud = ++solicitudLista

        actualizar.isEnabled = false
        mensaje.text = "Consultando personas..."
        lista.removeAllViews()

        Thread {
            val resultado = PersonasService.listar(token)

            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    solicitud != solicitudLista
                ) {
                    return@runOnUiThread
                }

                actualizar.isEnabled = true

                if (!resultado.correcto) {
                    mensaje.text = resultado.mensaje
                    return@runOnUiThread
                }

                val personas = resultado.datos.orEmpty()

                mensaje.text = if (personas.isEmpty()) {
                    "No hay personas con membresía activa."
                } else {
                    "Personas encontradas: ${personas.size}"
                }

                personas.forEach { persona ->
                    val documento = if (
                        persona.numeroDocumento.isBlank()
                    ) {
                        "Sin documento registrado"
                    } else {
                        "${persona.tipoDocumento}: " +
                        persona.numeroDocumento
                    }

                    val item = TextView(this).apply {
                        text =
                            "${persona.nombres} " +
                            "${persona.apellidos}\n" +
                            "$documento\n" +
                            "Congregación: " +
                            persona.congregacionNombre +
                            "\nDistrito: " +
                            persona.distritoNombre

                        textSize = 16f
                        setPadding(12, 18, 12, 18)
                    }

                    lista.addView(item)
                }
            }
        }.start()
    }
}
