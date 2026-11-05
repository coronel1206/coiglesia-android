package com.coiglesia.app.distritos

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DistritosActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private lateinit var lista: LinearLayout
    private lateinit var mensaje: TextView
    private lateinit var codigo: EditText
    private lateinit var nombre: EditText
    private lateinit var descripcion: EditText
    private lateinit var guardar: Button
    private lateinit var consultar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(EXTRA_TOKEN)

        if (token.isNullOrBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 20)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val titulo = TextView(this).apply {
            text = "Administración de Distritos"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 51, 86))
        }

        codigo = EditText(this).apply {
            hint = "Código del distrito"
            isSingleLine = true
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(20)
            )
        }

        nombre = EditText(this).apply {
            hint = "Nombre del distrito"
            isSingleLine = true
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(150)
            )
        }

        descripcion = EditText(this).apply {
            hint = "Descripción (opcional)"
            minLines = 2
        }

        guardar = Button(this).apply {
            text = "Crear distrito"
            isAllCaps = false
        }

        consultar = Button(this).apply {
            text = "Actualizar lista"
            isAllCaps = false
        }

        mensaje = TextView(this).apply {
            text = "Consultando distritos..."
            textSize = 15f
            setPadding(0, 14, 0, 14)
        }

        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(lista)

        val volver = Button(this).apply {
            text = "Volver al menú"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        root.addView(titulo)
        root.addView(codigo)
        root.addView(nombre)
        root.addView(descripcion)
        root.addView(guardar)
        root.addView(consultar)
        root.addView(mensaje)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(volver)

        setContentView(root)

        consultar.setOnClickListener {
            cargarDistritos(token)
        }

        guardar.setOnClickListener {
            val c = codigo.text.toString().trim()
            val n = nombre.text.toString().trim()
            val d = descripcion.text.toString().trim()

            if (c.isBlank() || n.isBlank()) {
                mensaje.text = "Código y nombre son obligatorios."
                return@setOnClickListener
            }

            guardar.isEnabled = false
            mensaje.text = "Guardando distrito..."

            Thread {
                val resultado = DistritosService.crear(
                    token, c, n, d
                )

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    guardar.isEnabled = true
                    mensaje.text = resultado.mensaje

                    if (resultado.correcto) {
                        codigo.text.clear()
                        nombre.text.clear()
                        descripcion.text.clear()
                        cargarDistritos(token)
                    }
                }
            }.start()
        }

        cargarDistritos(token)
    }

    private fun cargarDistritos(token: String) {
        consultar.isEnabled = false
        mensaje.text = "Consultando distritos..."

        Thread {
            val resultado = DistritosService.listar(token)

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                consultar.isEnabled = true
                lista.removeAllViews()
                mensaje.text = resultado.mensaje

                if (resultado.correcto) {
                    val distritos = resultado.datos.orEmpty()

                    if (distritos.isEmpty()) {
                        mensaje.text = "No hay distritos registrados."
                    }

                    distritos.forEach { distrito ->
                        val item = TextView(this).apply {
                            text = "${distrito.codigo} - ${distrito.nombre}\n" +
                                "Estado: ${distrito.estado}\n" +
                                distrito.descripcion
                            textSize = 16f
                            setPadding(12, 18, 12, 18)
                        }

                        lista.addView(item)
                    }
                }
            }
        }.start()
    }
}
