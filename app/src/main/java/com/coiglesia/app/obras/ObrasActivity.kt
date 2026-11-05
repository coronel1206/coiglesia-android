package com.coiglesia.app.obras

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import com.coiglesia.app.distritos.DistritosService
import com.coiglesia.app.congregaciones.CongregacionesService

class ObrasActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private var token = ""
    private var distritos = emptyList<DistritosService.Distrito>()
    private var congregaciones =
        emptyList<CongregacionesService.Congregacion>()

    private var solicitudCongregaciones = 0
    private var solicitudObras = 0

    private lateinit var selectorDistrito: Spinner
    private lateinit var selectorCongregacion: Spinner
    private lateinit var codigo: EditText
    private lateinit var nombre: EditText
    private lateinit var municipio: EditText
    private lateinit var departamento: EditText
    private lateinit var direccion: EditText
    private lateinit var guardar: Button
    private lateinit var actualizar: Button
    private lateinit var mensaje: TextView
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
            text = "Obras en Proyección"
            textSize = 24f
            setTextColor(Color.rgb(25, 51, 86))
            setPadding(0, 0, 0, 14)
        }

        selectorDistrito = Spinner(this)
        selectorCongregacion = Spinner(this)

        codigo = campo("Código de la obra", 30)
        nombre = campo("Nombre de la obra", 180)
        municipio = campo("Municipio")
        departamento = campo("Departamento")
        direccion = campo("Dirección de referencia")

        guardar = Button(this).apply {
            text = "Registrar obra"
            isAllCaps = false
            isEnabled = false
        }

        actualizar = Button(this).apply {
            text = "Actualizar obras"
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

        val volver = Button(this).apply {
            text = "Volver al menú"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        contenido.addView(titulo)
        contenido.addView(etiqueta("Distrito"))
        contenido.addView(selectorDistrito)
        contenido.addView(etiqueta("Congregación de origen"))
        contenido.addView(selectorCongregacion)
        contenido.addView(codigo)
        contenido.addView(nombre)
        contenido.addView(municipio)
        contenido.addView(departamento)
        contenido.addView(direccion)
        contenido.addView(guardar)
        contenido.addView(actualizar)
        contenido.addView(mensaje)
        contenido.addView(etiqueta("Obras registradas"))
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

        selectorCongregacion.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    cargarObras()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {}
            }

        actualizar.setOnClickListener { cargarObras() }
        guardar.setOnClickListener { crearObra() }

        cargarDistritos()
    }

    private fun campo(
        texto: String,
        limite: Int = 200
    ): EditText {
        return EditText(this).apply {
            hint = texto
            isSingleLine = true
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(limite)
            )
        }
    }

    private fun etiqueta(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            textSize = 17f
            setPadding(0, 14, 0, 6)
        }
    }

    private fun distritoSeleccionado():
        DistritosService.Distrito? {
        return distritos.getOrNull(
            selectorDistrito.selectedItemPosition
        )
    }

    private fun congregacionSeleccionada():
        CongregacionesService.Congregacion? {
        return congregaciones.getOrNull(
            selectorCongregacion.selectedItemPosition
        )
    }

    private fun cargarDistritos() {
        guardar.isEnabled = false
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

                val opciones = distritos.map {
                    "${it.codigo} - ${it.nombre}"
                }

                selectorDistrito.adapter = adaptador(opciones)
            }
        }.start()
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

    private fun cargarCongregaciones() {
        val distrito = distritoSeleccionado() ?: return
        val solicitud = ++solicitudCongregaciones
        ++solicitudObras

        congregaciones = emptyList()
        selectorCongregacion.adapter =
            adaptador(emptyList())
        lista.removeAllViews()
        guardar.isEnabled = false
        actualizar.isEnabled = false
        mensaje.text = "Cargando congregaciones..."

        Thread {
            val resultado = CongregacionesService.listar(
                token,
                distrito.id
            )

            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    solicitud != solicitudCongregaciones
                ) {
                    return@runOnUiThread
                }

                if (!resultado.correcto) {
                    mensaje.text = resultado.mensaje
                    return@runOnUiThread
                }

                congregaciones = resultado.datos.orEmpty()
                    .filter { it.estado == "activa" }

                if (congregaciones.isEmpty()) {
                    mensaje.text =
                        "No hay congregaciones activas en este distrito."
                    return@runOnUiThread
                }

                selectorCongregacion.adapter = adaptador(
                    congregaciones.map {
                        "${it.codigo} - ${it.nombre}"
                    }
                )
            }
        }.start()
    }

    private fun cargarObras() {
        val congregacion = congregacionSeleccionada()
            ?: return

        val solicitud = ++solicitudObras
        val congregacionId = congregacion.id

        guardar.isEnabled = true
        actualizar.isEnabled = false
        mensaje.text = "Consultando obras..."
        lista.removeAllViews()

        Thread {
            val resultado = ObrasService.listar(
                token,
                congregacionId
            )

            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    solicitud != solicitudObras
                ) {
                    return@runOnUiThread
                }

                actualizar.isEnabled = true
                mensaje.text = resultado.mensaje

                if (!resultado.correcto) {
                    return@runOnUiThread
                }

                val obras = resultado.datos.orEmpty()

                if (obras.isEmpty()) {
                    mensaje.text =
                        "No hay obras registradas en esta congregación."
                }

                obras.forEach { obra ->
                    val item = TextView(this).apply {
                        text =
                            "${obra.codigo} - ${obra.nombre}\n" +
                            "${obra.municipio}, ${obra.departamento}\n" +
                            "Estado: ${obra.estado}\n" +
                            "Dirección: ${obra.direccionReferencia}"
                        textSize = 16f
                        setPadding(12, 16, 12, 16)
                    }
                    lista.addView(item)
                }
            }
        }.start()
    }

    private fun crearObra() {
        val congregacion = congregacionSeleccionada()

        if (congregacion == null) {
            mensaje.text = "Seleccione una congregación."
            return
        }

        val c = codigo.text.toString().trim()
        val n = nombre.text.toString().trim()

        if (c.isBlank() || n.isBlank()) {
            mensaje.text =
                "Código y nombre son obligatorios."
            return
        }

        guardar.isEnabled = false
        mensaje.text = "Registrando obra..."

        Thread {
            val resultado = ObrasService.crear(
                token,
                congregacion.id,
                c,
                n,
                municipio.text.toString(),
                departamento.text.toString(),
                direccion.text.toString()
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
                    direccion.text.clear()
                    cargarObras()
                }
            }
        }.start()
    }
}
