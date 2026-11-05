package com.coiglesia.app.personas

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.view.View

class PersonasActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "access_token"
    }

    private lateinit var mensaje: TextView
    private lateinit var lista: LinearLayout
    private lateinit var actualizar: Button
    private lateinit var formulario: LinearLayout
    private lateinit var botonRegistro: Button
    private lateinit var selectorDistrito: Spinner
    private lateinit var selectorCongregacion: Spinner

    private lateinit var campoNombres: EditText
    private lateinit var campoApellidos: EditText
    private lateinit var campoTipoDocumento: Spinner
    private lateinit var campoNumeroDocumento: EditText
    private lateinit var campoTelefono: EditText
    private lateinit var campoCorreo: EditText
    private lateinit var campoFechaNacimiento: EditText
    private lateinit var botonGuardarPersona: Button

    private var token = ""
    private var solicitudLista = 0

    private var distritosRegistro =
        emptyList<PersonasService.DistritoRegistro>()
    private var solicitudDistritos = 0

    private var congregacionesRegistro =
        emptyList<PersonasService.CongregacionRegistro>()
    private var solicitudCongregaciones = 0

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

        botonRegistro = Button(this).apply {
            text = "Registrar nueva persona"
            isAllCaps = false
        }

        formulario = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }

        val etiquetaDistrito = TextView(this).apply {
            text = "Distrito"
            textSize = 16f
            setPadding(0, 12, 0, 4)
        }

        selectorDistrito = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PersonasActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Seleccione un distrito")
            )
        }

        val etiquetaCongregacion = TextView(this).apply {
            text = "Congregación"
            textSize = 16f
            setPadding(0, 12, 0, 4)
        }

        selectorCongregacion = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PersonasActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Seleccione una congregación")
            )
        }

        formulario.addView(etiquetaDistrito)
        formulario.addView(selectorDistrito)
        formulario.addView(etiquetaCongregacion)
        formulario.addView(selectorCongregacion)

        fun agregarCampo(
            etiqueta: String,
            sugerencia: String,
            tipo: Int = android.text.InputType.TYPE_CLASS_TEXT
        ): EditText {
            val tituloCampo = TextView(this).apply {
                text = etiqueta
                textSize = 16f
                setPadding(0, 12, 0, 4)
            }

            val campo = EditText(this).apply {
                hint = sugerencia
                inputType = tipo
                isSingleLine = true
            }

            formulario.addView(tituloCampo)
            formulario.addView(campo)
            return campo
        }

        campoNombres = agregarCampo(
            "Nombres *", "Nombres completos"
        )

        campoApellidos = agregarCampo(
            "Apellidos *", "Apellidos completos"
        )

        formulario.addView(TextView(this).apply {
            text = "Tipo de documento"
            textSize = 16f
            setPadding(0, 12, 0, 4)
        })

        campoTipoDocumento = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PersonasActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "Sin documento",
                    "CC",
                    "TI",
                    "RC",
                    "CE",
                    "Pasaporte"
                )
            )
        }

        formulario.addView(campoTipoDocumento)

        campoNumeroDocumento = agregarCampo(
            "Número de documento",
            "Número de identificación"
        )

        campoTelefono = agregarCampo(
            "Teléfono",
            "Número de contacto",
            android.text.InputType.TYPE_CLASS_PHONE
        )

        campoCorreo = agregarCampo(
            "Correo electrónico",
            "correo@ejemplo.com",
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        campoFechaNacimiento = agregarCampo(
            "Fecha de nacimiento",
            "AAAA-MM-DD (opcional)",
            android.text.InputType.TYPE_CLASS_DATETIME or
                android.text.InputType.TYPE_DATETIME_VARIATION_DATE
        )

        botonGuardarPersona = Button(this).apply {
            text = "Guardar persona"
            isAllCaps = false
            setOnClickListener { guardarPersona() }
        }

        formulario.addView(botonGuardarPersona)

        botonRegistro.setOnClickListener {
            val abrir = formulario.visibility != android.view.View.VISIBLE
            formulario.visibility = if (abrir) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            botonRegistro.text = if (abrir) {
                "Cerrar formulario"
            } else {
                "Registrar nueva persona"
            }
        }

        root.addView(titulo)
        root.addView(botonRegistro)
        root.addView(formulario)
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

        selectorDistrito.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                    limpiarCongregaciones()
                }

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 0 ||
                        position > distritosRegistro.size
                    ) {
                        limpiarCongregaciones()
                    } else {
                        val distrito = distritosRegistro[position - 1]
                        cargarCongregacionesRegistro(distrito.id)
                    }
                }
            }

        cargarPersonas()
        cargarDistritosRegistro()
    }

    private fun cargarDistritosRegistro() {
        val solicitud = ++solicitudDistritos

        selectorDistrito.isEnabled = false

        Thread {
            val resultado = PersonasService.listarDistritosRegistro(token)

            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    solicitud != solicitudDistritos
                ) {
                    return@runOnUiThread
                }

                if (!resultado.correcto) {
                    distritosRegistro = emptyList()
                    mensaje.text = resultado.mensaje
                    return@runOnUiThread
                }

                distritosRegistro = resultado.datos.orEmpty()

                val opciones = mutableListOf(
                    "Seleccione un distrito"
                )

                distritosRegistro.forEach {
                    opciones.add("${it.codigo} - ${it.nombre}")
                }

                selectorDistrito.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    opciones
                )

                selectorDistrito.isEnabled =
                    distritosRegistro.isNotEmpty()
            }
        }.start()
    }

    private fun limpiarCongregaciones() {
        solicitudCongregaciones++
        congregacionesRegistro = emptyList()

        selectorCongregacion.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Seleccione una congregación")
        )
        selectorCongregacion.isEnabled = false
    }

    private fun cargarCongregacionesRegistro(
        distritoId: String
    ) {
        limpiarCongregaciones()

        val solicitud = solicitudCongregaciones

        Thread {
            val resultado =
                PersonasService.listarCongregacionesRegistro(
                    token,
                    distritoId
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

                congregacionesRegistro = resultado.datos.orEmpty()

                val opciones = mutableListOf(
                    "Seleccione una congregación"
                )

                congregacionesRegistro.forEach {
                    opciones.add("${it.codigo} - ${it.nombre}")
                }

                selectorCongregacion.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    opciones
                )

                selectorCongregacion.isEnabled =
                    congregacionesRegistro.isNotEmpty()
            }
        }.start()
    }

    private fun guardarPersona() {
        val posicion = selectorCongregacion.selectedItemPosition

        if (posicion <= 0 ||
            posicion > congregacionesRegistro.size
        ) {
            mensaje.text = "Seleccione una congregación autorizada"
            return
        }

        val congregacion = congregacionesRegistro[posicion - 1]
        val nombres = campoNombres.text.toString().trim()
        val apellidos = campoApellidos.text.toString().trim()
        val tipoDocumento = if (
            campoTipoDocumento.selectedItemPosition == 0
        ) {
            ""
        } else {
            campoTipoDocumento.selectedItem.toString()
        }
        val numeroDocumento =
            campoNumeroDocumento.text.toString().trim()
        val telefono = campoTelefono.text.toString().trim()
        val correo = campoCorreo.text.toString().trim()
        val fecha = campoFechaNacimiento.text.toString().trim()

        if (nombres.isBlank() || apellidos.isBlank()) {
            mensaje.text = "Nombres y apellidos son obligatorios"
            return
        }

        if (tipoDocumento.isBlank() != numeroDocumento.isBlank()) {
            mensaje.text = "Indique tipo y número de documento"
            return
        }

        if (fecha.isNotBlank()) {
            try {
                val formato = java.text.SimpleDateFormat(
                    "yyyy-MM-dd",
                    java.util.Locale.US
                ).apply {
                    isLenient = false
                }

                val fechaValidada = formato.parse(fecha)
                if (fechaValidada == null ||
                    formato.format(fechaValidada) != fecha
                ) {
                    throw IllegalArgumentException()
                }
            } catch (_: Exception) {
                mensaje.text = "Fecha inválida. Use AAAA-MM-DD"
                return
            }
        }

        botonGuardarPersona.isEnabled = false
        mensaje.text = "Registrando persona..."

        Thread {
            val resultado = PersonasService.registrar(
                token = token,
                congregacionId = congregacion.id,
                nombres = nombres,
                apellidos = apellidos,
                tipoDocumento = tipoDocumento,
                numeroDocumento = numeroDocumento,
                telefono = telefono,
                correo = correo,
                fechaNacimiento = fecha
            )

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                botonGuardarPersona.isEnabled = true
                mensaje.text = resultado.mensaje

                if (resultado.correcto) {
                    campoNombres.text.clear()
                    campoApellidos.text.clear()
                    campoTipoDocumento.setSelection(0)
                    campoNumeroDocumento.text.clear()
                    campoTelefono.text.clear()
                    campoCorreo.text.clear()
                    campoFechaNacimiento.text.clear()

                    cargarPersonas()
                    Toast.makeText(
                        this,
                        "Persona y membresía registradas",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
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
