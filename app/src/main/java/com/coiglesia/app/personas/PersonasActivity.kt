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

    private var personaSeleccionada: PersonasService.Persona? = null

    private lateinit var panelHistorialMembresias: LinearLayout

    private lateinit var panelFinalizarMembresia: LinearLayout
    private lateinit var tituloFinalizarMembresia: TextView
    private lateinit var campoFechaSalidaMembresia: EditText
    private lateinit var campoObservacionesSalida: EditText
    private var membresiaSeleccionadaId: String? = null
    private lateinit var tituloHistorialMembresias: TextView
    private lateinit var contenidoHistorialMembresias: LinearLayout

    private lateinit var formularioEdicion: LinearLayout
    private lateinit var campoEditarNombres: EditText
    private lateinit var campoEditarApellidos: EditText
    private lateinit var campoEditarTipoDocumento: Spinner
    private lateinit var campoEditarNumeroDocumento: EditText
    private lateinit var campoEditarTelefono: EditText
    private lateinit var campoEditarCorreo: EditText
    private lateinit var campoEditarFechaNacimiento: EditText

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
        formularioEdicion = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(0, 12, 0, 12)
        }

        formularioEdicion.addView(TextView(this).apply {
            text = "Editar información personal"
            textSize = 20f
            setTextColor(Color.rgb(25, 51, 86))
        })

        fun campoEdicion(
            etiqueta: String,
            tipo: Int = android.text.InputType.TYPE_CLASS_TEXT
        ): EditText {
            formularioEdicion.addView(TextView(this).apply {
                text = etiqueta
                textSize = 16f
                setPadding(0, 10, 0, 4)
            })

            return EditText(this).apply {
                inputType = tipo
                isSingleLine = true
                formularioEdicion.addView(this)
            }
        }

        campoEditarNombres = campoEdicion("Nombres *")
        campoEditarApellidos = campoEdicion("Apellidos *")

        formularioEdicion.addView(TextView(this).apply {
            text = "Tipo de documento"
            textSize = 16f
        })

        campoEditarTipoDocumento = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PersonasActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "Sin documento", "CC", "TI",
                    "RC", "CE", "Pasaporte"
                )
            )
        }
        formularioEdicion.addView(campoEditarTipoDocumento)

        campoEditarNumeroDocumento =
            campoEdicion("Número de documento")

        campoEditarTelefono = campoEdicion(
            "Teléfono",
            android.text.InputType.TYPE_CLASS_PHONE
        )

        campoEditarCorreo = campoEdicion(
            "Correo electrónico",
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        campoEditarFechaNacimiento = campoEdicion(
            "Fecha de nacimiento (AAAA-MM-DD)",
            android.text.InputType.TYPE_CLASS_DATETIME or
                android.text.InputType.TYPE_DATETIME_VARIATION_DATE
        )

        formularioEdicion.addView(Button(this).apply {
            text = "Guardar cambios"
            isAllCaps = false

            setOnClickListener {
                val persona = personaSeleccionada

                if (persona == null) {
                    Toast.makeText(
                        this@PersonasActivity,
                        "Seleccione una persona",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val nombres =
                    campoEditarNombres.text.toString().trim()
                val apellidos =
                    campoEditarApellidos.text.toString().trim()

                val tipoDocumento =
                    if (campoEditarTipoDocumento.selectedItemPosition == 0)
                        ""
                    else
                        campoEditarTipoDocumento.selectedItem.toString()

                val numeroDocumento =
                    campoEditarNumeroDocumento.text.toString().trim()
                val telefono =
                    campoEditarTelefono.text.toString().trim()
                val correo =
                    campoEditarCorreo.text.toString().trim()
                val fecha =
                    campoEditarFechaNacimiento.text.toString().trim()

                if (nombres.isBlank() || apellidos.isBlank()) {
                    Toast.makeText(
                        this@PersonasActivity,
                        "Nombres y apellidos son obligatorios",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                if (tipoDocumento.isBlank() != numeroDocumento.isBlank()) {
                    Toast.makeText(
                        this@PersonasActivity,
                        "Indique tipo y numero de documento",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                if (fecha.isNotBlank()) {
                    try {
                        val formato = java.text.SimpleDateFormat(
                            "yyyy-MM-dd",
                            java.util.Locale.US
                        )
                        formato.isLenient = false
                        formato.parse(fecha)
                            ?: throw IllegalArgumentException()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@PersonasActivity,
                            "Fecha invalida. Use AAAA-MM-DD",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
                }

                isEnabled = false
                val botonGuardar = this

                Thread {
                    val resultado = PersonasService.actualizar(
                        token,
                        persona.id,
                        nombres,
                        apellidos,
                        tipoDocumento,
                        numeroDocumento,
                        telefono,
                        correo,
                        fecha
                    )

                    runOnUiThread {
                        botonGuardar.isEnabled = true

                        if (resultado.correcto) {
                            personaSeleccionada = null
                            formularioEdicion.visibility =
                                android.view.View.GONE

                            Toast.makeText(
                                this@PersonasActivity,
                                "Datos actualizados correctamente",
                                Toast.LENGTH_LONG
                            ).show()

                            cargarPersonas()
                        } else {
                            Toast.makeText(
                                this@PersonasActivity,
                                resultado.mensaje,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        })

        formularioEdicion.addView(Button(this).apply {
            text = "Cancelar edición"
            isAllCaps = false
            setOnClickListener {
                personaSeleccionada = null
                formularioEdicion.visibility = android.view.View.GONE
            }
        })

        root.addView(formularioEdicion)

        panelHistorialMembresias = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(0, 16, 0, 16)
        }

        tituloHistorialMembresias = TextView(this).apply {
            text = "Historial de membresías"
            textSize = 20f
            setTextColor(Color.rgb(25, 51, 86))
        }

        panelHistorialMembresias.addView(
            tituloHistorialMembresias
        )

        contenidoHistorialMembresias = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        panelHistorialMembresias.addView(
            contenidoHistorialMembresias
        )

        panelHistorialMembresias.addView(Button(this).apply {
            text = "Cerrar historial"
            isAllCaps = false
            setOnClickListener {
                panelHistorialMembresias.visibility =
                    android.view.View.GONE
            }
        })

        root.addView(panelHistorialMembresias)

        panelFinalizarMembresia = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(0, 16, 0, 16)
        }

        tituloFinalizarMembresia = TextView(this).apply {
            text = "Finalizar membresía"
            textSize = 20f
            setTextColor(Color.rgb(140, 40, 40))
        }

        panelFinalizarMembresia.addView(
            tituloFinalizarMembresia
        )

        panelFinalizarMembresia.addView(TextView(this).apply {
            text = "Fecha de salida (AAAA-MM-DD)"
            textSize = 16f
        })

        campoFechaSalidaMembresia = EditText(this).apply {
            hint = "Dejar vacío para usar la fecha actual"
            inputType =
                android.text.InputType.TYPE_CLASS_DATETIME or
                android.text.InputType.TYPE_DATETIME_VARIATION_DATE
            isSingleLine = true
        }

        panelFinalizarMembresia.addView(
            campoFechaSalidaMembresia
        )

        panelFinalizarMembresia.addView(TextView(this).apply {
            text = "Observaciones de salida"
            textSize = 16f
        })

        campoObservacionesSalida = EditText(this).apply {
            hint = "Motivo u observaciones"
            minLines = 3
            maxLines = 5
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        panelFinalizarMembresia.addView(
            campoObservacionesSalida
        )


        panelFinalizarMembresia.addView(Button(this).apply {
            text = "Confirmar finalización"
            isAllCaps = false

            setOnClickListener {
                val membresiaId = membresiaSeleccionadaId

                if (membresiaId.isNullOrBlank()) {
                    Toast.makeText(
                        this@PersonasActivity,
                        "Seleccione una membresía",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val fecha =
                    campoFechaSalidaMembresia.text.toString().trim()

                val observaciones =
                    campoObservacionesSalida.text.toString().trim()

                if (observaciones.length > 2000) {
                    Toast.makeText(
                        this@PersonasActivity,
                        "Las observaciones superan 2000 caracteres",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                android.app.AlertDialog.Builder(this@PersonasActivity)
                    .setTitle("¿Finalizar membresía?")
                    .setMessage(
                        "La persona dejará de tener una membresía activa " +
                        "en esta congregación. Su historial se conservará. " +
                        "¿Desea continuar?"
                    )
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Sí, finalizar") { _, _ ->
                        isEnabled = false
                        val botonConfirmar = this

                        Thread {
                            val resultado =
                                PersonasService.finalizarMembresia(
                                    token,
                                    membresiaId,
                                    fecha,
                                    observaciones
                                )

                            runOnUiThread {
                                botonConfirmar.isEnabled = true

                                if (resultado.correcto) {
                                    membresiaSeleccionadaId = null

                                    panelFinalizarMembresia.visibility =
                                        android.view.View.GONE

                                    panelHistorialMembresias.visibility =
                                        android.view.View.GONE

                                    Toast.makeText(
                                        this@PersonasActivity,
                                        "Membresía finalizada correctamente",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    cargarPersonas()
                                } else {
                                    Toast.makeText(
                                        this@PersonasActivity,
                                        resultado.mensaje,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }.start()
                    }
                    .show()
            }
        })

        panelFinalizarMembresia.addView(Button(this).apply {
            text = "Cancelar finalización"
            isAllCaps = false
            setOnClickListener {
                membresiaSeleccionadaId = null
                panelFinalizarMembresia.visibility =
                    android.view.View.GONE
            }
        })

        root.addView(panelFinalizarMembresia)
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

                    val botonEditar = Button(this).apply {
                        text = "Editar datos"
                        isAllCaps = false

                        setOnClickListener {
                            personaSeleccionada = persona

                            campoEditarNombres.setText(persona.nombres)
                            campoEditarApellidos.setText(persona.apellidos)

                            val tipos = listOf(
                                "", "CC", "TI", "RC", "CE", "Pasaporte"
                            )

                            val tipoActual =
                                persona.tipoDocumento.orEmpty()

                            val indiceTipo = tipos.indexOfFirst {
                                it.equals(tipoActual, ignoreCase = true)
                            }

                            campoEditarTipoDocumento.setSelection(
                                if (indiceTipo >= 0) indiceTipo else 0
                            )

                            campoEditarNumeroDocumento.setText(
                                persona.numeroDocumento.orEmpty()
                            )

                            campoEditarTelefono.setText(
                                persona.telefono.orEmpty()
                            )

                            campoEditarCorreo.setText(
                                persona.correoElectronico.orEmpty()
                            )

                            campoEditarFechaNacimiento.setText(
                                persona.fechaNacimiento.orEmpty()
                            )

                            formularioEdicion.visibility =
                                android.view.View.VISIBLE

                            Toast.makeText(
                                this@PersonasActivity,
                                "Persona seleccionada: " +
                                    persona.nombres + " " +
                                    persona.apellidos,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    lista.addView(botonEditar)

                    val botonHistorial = Button(this).apply {
                        text = "Ver historial"
                        isAllCaps = false

                        setOnClickListener {
                            val personaId = persona.id

                            panelHistorialMembresias.visibility =
                                android.view.View.VISIBLE

                            tituloHistorialMembresias.text =
                                "Historial: ${persona.nombres} ${persona.apellidos}"

                            tituloHistorialMembresias.tag = personaId
                            contenidoHistorialMembresias.removeAllViews()

                            contenidoHistorialMembresias.addView(
                                TextView(this@PersonasActivity).apply {
                                    text = "Consultando membresías..."
                                    textSize = 16f
                                }
                            )

                            Thread {
                                val resultado =
                                    PersonasService.listarMembresias(
                                        token,
                                        personaId
                                    )

                                runOnUiThread {
                                    if (tituloHistorialMembresias.tag != personaId) {
                                        return@runOnUiThread
                                    }

                                    contenidoHistorialMembresias.removeAllViews()

                                    if (!resultado.correcto) {
                                        contenidoHistorialMembresias.addView(
                                            TextView(this@PersonasActivity).apply {
                                                text = resultado.mensaje
                                                textSize = 16f
                                            }
                                        )
                                    } else {
                                        val membresias =
                                            resultado.datos.orEmpty()

                                        if (membresias.isEmpty()) {
                                            contenidoHistorialMembresias.addView(
                                                TextView(this@PersonasActivity).apply {
                                                    text = "No hay membresías disponibles"
                                                    textSize = 16f
                                                }
                                            )
                                        }

                                        membresias.forEach { membresia ->
                                            val detalle = """
                                                Congregación: ${membresia.congregacionNombre}
                                                Distrito: ${membresia.distritoNombre}
                                                Ingreso: ${membresia.fechaIngreso}
                                                Salida: ${membresia.fechaSalida ?: "Sin registrar"}
                                                Estado: ${membresia.estado}
                                                Observaciones: ${membresia.observaciones ?: "Sin observaciones"}
                                            """.trimIndent()

                                            contenidoHistorialMembresias.addView(
                                                TextView(this@PersonasActivity).apply {
                                                    text = detalle
                                                    textSize = 15f
                                                    setPadding(0, 12, 0, 16)
                                                }
                                            )

                                            if (membresia.estado == "activa") {
                                                val botonPreparar = Button(
                                                    this@PersonasActivity
                                                ).apply {
                                                    text = "Preparar finalización"
                                                    isAllCaps = false

                                                    setOnClickListener {
                                                        membresiaSeleccionadaId =
                                                            membresia.id

                                                        tituloFinalizarMembresia.text =
                                                            "Finalizar membresía: " +
                                                            membresia.congregacionNombre

                                                        campoFechaSalidaMembresia.setText("")
                                                        campoObservacionesSalida.setText("")

                                                        panelFinalizarMembresia.visibility =
                                                            android.view.View.VISIBLE
                                                    }
                                                }

                                                contenidoHistorialMembresias.addView(
                                                    botonPreparar
                                                )
                                            }

                                        }
                                    }
                                }
                            }.start()
                        }
                    }

                    lista.addView(botonHistorial)
                }
            }
        }.start()
    }
}
