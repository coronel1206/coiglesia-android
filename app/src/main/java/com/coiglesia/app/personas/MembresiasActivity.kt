package com.coiglesia.app.personas

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.util.concurrent.Executors

/** Consulta origen antes de cada operación y nunca reintenta escrituras automáticamente. */
class MembresiasActivity : Activity() {
    private val executor = Executors.newFixedThreadPool(2)
    private var token = ""
    private var generation = 0
    private var busy = false
    private var ready = false
    private var personas = emptyList<PersonasService.Persona>()
    private var filtradas = emptyList<PersonasService.Persona>()
    private var distritos = emptyList<PersonasService.DistritoRegistro>()
    private var destinos = emptyList<PersonasService.CongregacionRegistro>()
    private var visibles = emptyList<PersonasService.CongregacionRegistro>()
    private var persona: PersonasService.Persona? = null
    private var activa: PersonasService.Membresia? = null
    private lateinit var personasSpinner: Spinner
    private lateinit var distritosSpinner: Spinner
    private lateinit var destinosSpinner: Spinner
    private lateinit var buscar: EditText
    private lateinit var fecha: EditText
    private lateinit var notas: EditText
    private lateinit var origen: TextView
    private lateinit var estado: TextView
    private lateinit var historial: TextView
    private lateinit var guardar: Button
    private lateinit var recargar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(PersonasActivity.EXTRA_TOKEN).orEmpty()
        if (token.isBlank()) { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(android.graphics.Color.rgb(247, 249, 252))
        }
        fun label(value: String): TextView = TextView(this).apply {
            text = value; textSize = 16f; setPadding(0, dp(12), 0, dp(6)); root.addView(this)
        }
        label("COIGLESIA · Membresías y traslados").apply {
            textSize = 24f; setTextColor(android.graphics.Color.rgb(25, 51, 86))
        }
        label("Seleccione una persona. Su membresía vigente determinará si corresponde un traslado o un nuevo ingreso.")
        buscar = EditText(this).apply { hint = "Buscar nombre o documento"; isSingleLine = true }
        root.addView(buscar)
        label("Persona")
        personasSpinner = Spinner(this); root.addView(personasSpinner)
        origen = label("Origen: seleccione una persona")
        label("Distrito de destino")
        distritosSpinner = Spinner(this); root.addView(distritosSpinner)
        label("Congregación de destino")
        destinosSpinner = Spinner(this); root.addView(destinosSpinner)
        label("Fecha de ingreso o traslado")
        fecha = EditText(this).apply {
            setText(LocalDate.now().toString()); isSingleLine = true
            hint = "AAAA-MM-DD"; contentDescription = "Fecha de ingreso o traslado"
        }
        root.addView(fecha)
        root.addView(Button(this).apply {
            text = "Elegir fecha"; isAllCaps = false
            setOnClickListener {
                if (!busy) {
                    val now = LocalDate.now()
                    DatePickerDialog(this@MembresiasActivity, { _, y, m, d ->
                        fecha.setText(LocalDate.of(y, m + 1, d).toString())
                    }, now.year, now.monthValue - 1, now.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }
            }
        })
        label("Observaciones (opcional, máximo 2000 caracteres)")
        notas = EditText(this).apply {
            minLines = 2; maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
        }
        root.addView(notas)
        estado = label("Consultando datos autorizados…").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        guardar = Button(this).apply { text = "Continuar"; isAllCaps = false; isEnabled = false }
        root.addView(guardar)
        recargar = Button(this).apply {
            text = "Actualizar información"; isAllCaps = false
            setOnClickListener { if (!busy) cargarCatalogos() }
        }
        root.addView(recargar)
        label("Historial autorizado")
        historial = label("Seleccione una persona para consultar su historial.")
        root.addView(Button(this).apply {
            text = "Volver"; isAllCaps = false
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        buscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filtrar() }
        })
        personasSpinner.onItemSelectedListener = seleccion { position ->
            persona = filtradas.getOrNull(position - 1)
            consultarOrigen()
        }
        distritosSpinner.onItemSelectedListener = seleccion { position ->
            val id = distritos.getOrNull(position - 1)?.id
            visibles = destinos.filter { it.distritoId == id }
            opciones(destinosSpinner, "Seleccione una congregación", visibles.map { "${it.codigo} · ${it.nombre}" })
            destinosSpinner.isEnabled = !busy && visibles.isNotEmpty()
        }
        guardar.setOnClickListener { confirmar() }
        cargarCatalogos()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun seleccion(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) { action(0) }
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { action(position) }
    }
    private fun opciones(spinner: Spinner, titulo: String, values: List<String>) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(titulo) + values)
    }
    private fun controles() {
        buscar.isEnabled = !busy
        personasSpinner.isEnabled = !busy
        distritosSpinner.isEnabled = !busy && distritos.isNotEmpty()
        destinosSpinner.isEnabled = !busy && visibles.isNotEmpty()
        fecha.isEnabled = !busy; notas.isEnabled = !busy; recargar.isEnabled = !busy
        guardar.isEnabled = !busy && ready && persona != null
        guardar.text = if (activa != null) "Confirmar traslado" else "Registrar nueva membresía"
    }
    private fun filtrar() {
        generation++; ready = false; activa = null; persona = null
        val filter = buscar.text.toString().trim()
        filtradas = personas.filter { "${it.nombres} ${it.apellidos} ${it.numeroDocumento}".contains(filter, true) }
        opciones(personasSpinner, "Seleccione una persona", filtradas.map { "${it.nombres} ${it.apellidos} · ${it.numeroDocumento}" })
        origen.text = "Origen: seleccione una persona"; historial.text = ""
        controles()
    }
    private fun cargarCatalogos() {
        val request = ++generation
        busy = true; ready = false; controles(); estado.text = "Consultando datos autorizados…"
        executor.execute {
            val p = PersonasService.listar(token)
            val d = PersonasService.listarDistritosMembresia(token)
            val c = PersonasService.listarDestinosMembresia(token)
            runOnUiThread {
                if (isFinishing || isDestroyed || request != generation) return@runOnUiThread
                busy = false
                if (!p.correcto || !d.correcto || !c.correcto) {
                    estado.text = listOf(p, d, c).filter { !it.correcto }.joinToString("\n") { it.mensaje }
                    controles(); return@runOnUiThread
                }
                personas = p.datos.orEmpty(); distritos = d.datos.orEmpty(); destinos = c.datos.orEmpty()
                opciones(distritosSpinner, "Seleccione un distrito", distritos.map { "${it.codigo} · ${it.nombre}" })
                filtrar()
                estado.text = if (destinos.isEmpty()) "No tiene destinos autorizados para registrar membresías." else "Seleccione persona y destino."
            }
        }
    }
    private fun consultarOrigen() {
        val request = ++generation
        val selected = persona
        ready = false; activa = null; controles()
        if (selected == null) { origen.text = "Origen: seleccione una persona"; historial.text = ""; return }
        origen.text = "Consultando membresía vigente…"
        executor.execute {
            val result = PersonasService.listarMembresias(token, selected.id)
            runOnUiThread {
                if (isFinishing || isDestroyed || request != generation) return@runOnUiThread
                if (!result.correcto) {
                    origen.text = "No se pudo verificar el origen"; estado.text = result.mensaje
                    historial.text = ""; controles(); return@runOnUiThread
                }
                val records = result.datos.orEmpty()
                val active = records.filter { it.estado == "activa" }
                if (active.size > 1) { estado.text = "Inconsistencia: varias membresías activas. Operación bloqueada."; return@runOnUiThread }
                activa = active.singleOrNull(); ready = true
                origen.text = activa?.let { "MEMBRESÍA ACTIVA\nOrigen: ${it.congregacionNombre}\nDistrito: ${it.distritoNombre}\nIngreso: ${it.fechaIngreso}" }
                    ?: "Sin membresía activa visible. El servidor verificará si puede registrar un nuevo ingreso."
                historial.text = records.joinToString("\n\n") {
                    "${it.congregacionNombre} · ${it.distritoNombre}\n${it.fechaIngreso} → ${it.fechaSalida ?: "Vigente"}\n${it.estado}\n${it.observaciones.orEmpty()}"
                }.ifBlank { "No hay membresías autorizadas para mostrar." }
                controles()
            }
        }
    }
    private fun confirmar() {
        if (busy || !ready) return
        val selected = persona ?: return
        val destino = visibles.getOrNull(destinosSpinner.selectedItemPosition - 1)
        if (destino == null) { estado.text = "Seleccione una congregación de destino"; return }
        val source = activa
        if (source?.congregacionId == destino.id) { estado.text = "El destino debe ser diferente del origen"; return }
        val day = fecha.text.toString().trim(); val note = notas.text.toString().trim()
        ValidacionMembresia.validar(day, note, source?.fechaIngreso)?.let { estado.text = it; return }
        busy = true; controles()
        val title = if (source == null) "Confirmar nueva membresía" else "Confirmar traslado"
        AlertDialog.Builder(this).setTitle(title)
            .setMessage("Persona: ${selected.nombres} ${selected.apellidos}\nOrigen: ${source?.congregacionNombre ?: "Sin membresía activa visible"}\nDestino: ${destino.nombre}\nFecha: $day\n\nEl historial se conservará.")
            .setNegativeButton("Cancelar") { _, _ -> busy = false; controles() }
            .setOnCancelListener { busy = false; controles() }
            .setPositiveButton("Confirmar") { _, _ ->
                estado.text = "Procesando. Espere la confirmación…"
                executor.execute {
                    val result = if (source != null) PersonasService.trasladarPersona(token, selected.id, destino.id, day, note, source.id)
                        else PersonasService.registrarMembresia(token, selected.id, destino.id, day, note)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        busy = false
                        estado.text = if (result.correcto) result.mensaje else "${result.mensaje}\nRevise el historial antes de repetir: una interrupción de red no confirma que la operación haya fallado."
                        if (result.correcto) { setResult(RESULT_OK); notas.text.clear() }
                        // También reconsulta tras errores de red; nunca repite una escritura automáticamente.
                        consultarOrigen()
                    }
                }
            }.show()
    }
    @Deprecated("Activity back compatibility")
    override fun onBackPressed() {
        if (busy) { Toast.makeText(this, "Espere a que termine la consulta u operación", Toast.LENGTH_SHORT).show() }
        else super.onBackPressed()
    }
    override fun onDestroy() { generation++; executor.shutdownNow(); super.onDestroy() }
}
