package com.coiglesia.app.congregaciones

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.coiglesia.app.distritos.DistritosService

class CongregacionesActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private lateinit var selector: Spinner
    private lateinit var codigo: EditText
    private lateinit var nombre: EditText
    private lateinit var municipio: EditText
    private lateinit var departamento: EditText
    private lateinit var mensaje: TextView
    private lateinit var lista: LinearLayout
    private lateinit var guardar: Button
    private lateinit var actualizar: Button

    private var distritos =
        emptyList<DistritosService.Distrito>()

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
            text = "Administración de Congregaciones"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 51, 86))
        }

        selector = Spinner(this)

        codigo = EditText(this).apply {
            hint = "Código de la congregación"
            isSingleLine = true
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(30)
            )
        }

        nombre = EditText(this).apply {
            hint = "Nombre de la congregación"
            isSingleLine = true
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(180)
            )
        }

        municipio = EditText(this).apply {
            hint = "Municipio"
            isSingleLine = true
        }

        departamento = EditText(this).apply {
            hint = "Departamento"
            isSingleLine = true
        }

        guardar = Button(this).apply {
            text = "Crear congregación"
            isAllCaps = false
            isEnabled = false
        }

        actualizar = Button(this).apply {
            text = "Actualizar congregaciones"
            isAllCaps = false
            isEnabled = false
        }

        mensaje = TextView(this).apply {
            text = "Cargando distritos..."
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
        root.addView(selector)
        root.addView(codigo)
        root.addView(nombre)
        root.addView(municipio)
        root.addView(departamento)
        root.addView(guardar)
        root.addView(actualizar)
        root.addView(mensaje)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(volver)

        setContentView(root)

        selector.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    cargarCongregaciones()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {}
            }

        actualizar.setOnClickListener {
            cargarCongregaciones()
        }

        guardar.setOnClickListener {
            crearCongregacion()
        }

        cargarDistritos()
    }

    private fun distritoSeleccionado():
        DistritosService.Distrito? {
        val posicion = selector.selectedItemPosition
        return distritos.getOrNull(posicion)
    }

    private fun cargarDistritos() {
        guardar.isEnabled = false
        actualizar.isEnabled = false
        mensaje.text = "Cargando distritos..."

        Thread {
            val resultado = DistritosService.listarParaCongregaciones(token)

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

                val nombres = distritos.map {
                    "${it.codigo} - ${it.nombre}"
                }

                val adaptador = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    nombres
                )

                adaptador.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )

                selector.adapter = adaptador
                guardar.isEnabled = true
                actualizar.isEnabled = true
                mensaje.text = "Seleccione un distrito."
            }
        }.start()
    }

    private fun cargarCongregaciones() {
        val distrito = distritoSeleccionado() ?: return
        val id = distrito.id
        val solicitud = ++solicitudLista

        actualizar.isEnabled = false
        mensaje.text = "Consultando congregaciones..."
        lista.removeAllViews()

        Thread {
            val resultado = CongregacionesService.listar(
                token, id
            )

            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    solicitud != solicitudLista
                ) {
                    return@runOnUiThread
                }

                actualizar.isEnabled = true
                mensaje.text = resultado.mensaje

                if (!resultado.correcto) {
                    return@runOnUiThread
                }

                val congregaciones =
                    resultado.datos.orEmpty()

                if (congregaciones.isEmpty()) {
                    mensaje.text =
                        "No hay congregaciones en este distrito."
                }

                congregaciones.forEach { congregacion ->
                    val item = TextView(this).apply {
                        text =
                            "${congregacion.codigo} - " +
                            "${congregacion.nombre}\n" +
                            "${congregacion.municipio}, " +
                            "${congregacion.departamento}\n" +
                            "Estado: ${congregacion.estado}"
                        textSize = 16f
                        setPadding(12, 18, 12, 18)
                    }
                    lista.addView(item)
                    lista.addView(Button(this).apply {
                        text = "Editar congregación"; isAllCaps = false
                        setOnClickListener {
                            com.coiglesia.app.territorio.EditorTerritorial.mostrar(
                                this@CongregacionesActivity, token, "Editar congregación",
                                "actualizar_congregacion_app", congregacion.id,
                                listOf(
                                    com.coiglesia.app.territorio.EditorTerritorial.Campo("p_codigo", "Código", congregacion.codigo, 30),
                                    com.coiglesia.app.territorio.EditorTerritorial.Campo("p_nombre", "Nombre", congregacion.nombre, 180),
                                    com.coiglesia.app.territorio.EditorTerritorial.Campo("p_municipio", "Municipio", congregacion.municipio, 120),
                                    com.coiglesia.app.territorio.EditorTerritorial.Campo("p_departamento", "Departamento", congregacion.departamento, 120)
                                ), listOf("activa", "inactiva", "cerrada"), congregacion.estado
                            ) { cargarCongregaciones() }
                        }
                    })
                }
            }
        }.start()
    }

    private fun crearCongregacion() {
        val distrito = distritoSeleccionado()

        if (distrito == null) {
            mensaje.text = "Seleccione un distrito."
            return
        }

        val c = codigo.text.toString().trim()
        val n = nombre.text.toString().trim()
        val m = municipio.text.toString().trim()
        val d = departamento.text.toString().trim()

        if (c.isBlank() || n.isBlank()) {
            mensaje.text =
                "Código y nombre son obligatorios."
            return
        }

        guardar.isEnabled = false
        mensaje.text = "Guardando congregación..."

        Thread {
            val resultado = CongregacionesService.crear(
                token,
                distrito.id,
                c,
                n,
                m,
                d
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
                    municipio.text.clear()
                    departamento.text.clear()
                    cargarCongregaciones()
                }
            }
        }.start()
    }
}
