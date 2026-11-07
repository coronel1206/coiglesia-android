package com.coiglesia.app.eclesiastica

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.coiglesia.app.network.SupabaseHttp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Cada módulo usa las mismas RPC, que autorizan el tipo y territorio en el servidor. */
class EclesiasticaActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var filter: Spinner
    private lateinit var create: Button
    private var token = ""
    private var tipo = ""
    private var busy = false
    private var data = JSONObject()
    private val activityType get() = tipo in setOf("evento", "asamblea")
    private val titles = mapOf("ministerio" to "Ministerios", "comite" to "Comités", "musica" to "Músicos y equipos musicales", "evento" to "Eventos y asistencia", "asamblea" to "Asambleas")
    private val states get() = if (activityType) listOf("activo", "realizado", "cancelado", "inactivo") else listOf("activo", "inactivo")
    private fun rows(a: JSONArray?): List<JSONObject> = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
    private fun txt(o: JSONObject, k: String) = if (o.isNull(k)) "" else o.optString(k)
    private fun layout() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p=(16*resources.displayMetrics.density).toInt();setPadding(p,p,p,p) }
    private fun label(parent: LinearLayout, value: String, size: Float = 16f) = TextView(this).apply { text=value;textSize=size;setPadding(0,12,0,12);parent.addView(this) }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) = Button(this).apply { text=value;isAllCaps=false;parent.addView(this);setOnClickListener { if (!busy) action() } }
    private fun input(parent: LinearLayout, title: String, value: String, max: Int): EditText {
        label(parent,title)
        return EditText(this).apply { setText(value);filters=arrayOf(InputFilter.LengthFilter(max));parent.addView(this) }
    }
    private fun spinner(parent: LinearLayout, choices: List<String>) = Spinner(this).apply {
        adapter=ArrayAdapter(this@EclesiasticaActivity,android.R.layout.simple_spinner_dropdown_item,choices);parent.addView(this)
    }
    private fun enable(v: View, value: Boolean) { v.isEnabled=value;if(v is ViewGroup) for(i in 0 until v.childCount) enable(v.getChildAt(i),value) }
    private fun rpc(name: String, p: JSONObject) = SupabaseHttp.rpc(name,token,p)
    private fun scope(o: JSONObject): String = when {
        txt(o,"congregacion").isNotBlank() -> "${txt(o,"distrito")} · ${txt(o,"congregacion")}"
        txt(o,"distrito").isNotBlank() -> txt(o,"distrito")
        else -> "Nacional"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token=intent.getStringExtra("access_token").orEmpty();tipo=intent.getStringExtra("tipo").orEmpty()
        if(token.isBlank() || tipo !in titles) { finish();return }
        root=layout();root.setBackgroundColor(android.graphics.Color.rgb(247,249,252))
        label(root,titles.getValue(tipo),25f)
        status=label(root,"Consultando permisos y registros…")
        search=input(root,"Buscar por nombre o territorio","",180)
        filter=spinner(root,listOf("Todos")+states)
        create=button(root,"Crear registro") { edit(null) }
        button(root,"Actualizar") { load() }
        list=layout();root.addView(list)
        button(root,"Volver") { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { if(!busy) render() }
            override fun afterTextChanged(s:Editable?) {}
        })
        filter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) { if(!busy) render() }
            override fun onNothingSelected(p:AdapterView<*>?) {}
        }
        load()
    }
    private fun load() {
        if(busy) return
        busy=true;enable(root,false);status.text="Consultando información autorizada…"
        Thread {
            val r=runCatching { JSONObject(rpc("listar_eclesiastica_app",JSONObject().put("p_tipo",tipo))) }
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                busy=false;enable(root,true)
                r.fold({data=it;status.text="Solo se muestran registros autorizados."},{data=JSONObject();status.text=it.message})
                render()
            }
        }.start()
    }
    private fun render() {
        list.removeAllViews()
        create.isEnabled=data.optBoolean("nacional") || rows(data.optJSONArray("territorios")).isNotEmpty()
        val q=search.text.toString().trim()
        val state=filter.selectedItem?.toString() ?: "Todos"
        val visible=rows(data.optJSONArray("registros")).filter { r ->
            (state=="Todos" || r.getString("estado")==state) && (txt(r,"nombre")+" "+scope(r)).contains(q,true)
        }
        if(visible.isEmpty()) label(list,"No hay registros para este filtro o no tiene permisos para consultarlos.")
        visible.forEach { r ->
            val card=layout();list.addView(card)
            label(card,r.getString("nombre"),20f)
            label(card,"${scope(r)}\nEstado: ${r.getString("estado")}\nIntegrantes activos: ${r.getLong("integrantes")}"+
                if(activityType) "\nFecha: ${txt(r,"fecha")} · ${txt(r,"lugar")}\nAsistencias: ${r.getLong("asistentes")}" else "")
            if(txt(r,"descripcion").isNotBlank()) label(card,txt(r,"descripcion"))
            if(r.optBoolean("editable")) button(card,"Editar / cambiar estado") { edit(r) }
            button(card,if(activityType) "Participantes y asistencia" else "Integrantes") { participants(r) }
        }
    }
    private fun edit(r: JSONObject?) {
        val form=layout()
        val scopes=if(r!=null) listOf(r) else buildList {
            if(data.optBoolean("nacional")) add(JSONObject())
            addAll(rows(data.optJSONArray("territorios")))
        }
        if(scopes.isEmpty()) return
        label(form,"Territorio")
        val territory=spinner(form,scopes.map { scope(it) });territory.isEnabled=r==null
        val name=input(form,"Nombre",r?.let {txt(it,"nombre")}.orEmpty(),180)
        val description=input(form,"Descripción",r?.let {txt(it,"descripcion")}.orEmpty(),4000)
        val date=if(activityType) input(form,"Fecha (AAAA-MM-DD)",r?.let {txt(it,"fecha")}.orEmpty(),10) else EditText(this)
        date.visibility=if(activityType) View.VISIBLE else View.GONE
        date.isFocusable=false
        date.setOnClickListener {
            val d=runCatching { LocalDate.parse(date.text) }.getOrDefault(LocalDate.now())
            DatePickerDialog(this,{_,y,m,day ->date.setText(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
        }
        val place=input(form,"Lugar",r?.let {txt(it,"lugar")}.orEmpty(),250)
        label(form,"Estado");val state=spinner(form,states)
        state.setSelection(states.indexOf(r?.optString("estado") ?: "activo").coerceAtLeast(0))
        val error=label(form,"")
        val dialog=AlertDialog.Builder(this).setTitle(if(r==null) "Crear registro" else "Editar registro")
            .setView(ScrollView(this).apply {addView(form)}).setPositiveButton("Revisar y guardar",null).setNegativeButton("Cancelar",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(busy) return@setOnClickListener
                if(name.text.isBlank()) {error.text="Escriba el nombre.";return@setOnClickListener}
                if(activityType && runCatching {LocalDate.parse(date.text)}.isFailure) {error.text="Seleccione una fecha válida.";return@setOnClickListener}
                val chosen=scopes[territory.selectedItemPosition]
                val p=JSONObject().put("p_id",r?.getString("id") ?: JSONObject.NULL).put("p_tipo",tipo)
                    .put("p_nombre",name.text.toString().trim()).put("p_descripcion",description.text.toString().trim())
                    .put("p_distrito_id",chosen.opt("distrito_id") ?: JSONObject.NULL)
                    .put("p_congregacion_id",chosen.opt("congregacion_id") ?: JSONObject.NULL)
                    .put("p_fecha",if(activityType) date.text.toString() else JSONObject.NULL)
                    .put("p_lugar",place.text.toString().trim()).put("p_estado",state.selectedItem.toString())
                AlertDialog.Builder(this).setTitle("Confirmar operación")
                    .setMessage("${name.text}\n${scope(chosen)}\nEstado: ${state.selectedItem}")
                    .setNegativeButton("Volver",null).setPositiveButton("Guardar") {_,_->
                        save(dialog,form,error,"guardar_eclesiastica_app",p) { load() }
                    }.show()
            }
        }
        dialog.show()
    }
    private fun save(dialog: AlertDialog, form: LinearLayout, error: TextView, function: String, p: JSONObject, done: () -> Unit) {
        if(busy) return
        busy=true;enable(root,false);enable(form,false);dialog.setCancelable(false)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false;dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=false
        error.text="Guardando…"
        Thread {
            val result=runCatching {rpc(function,p)}
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                busy=false;enable(root,true)
                if(result.isSuccess) {dialog.dismiss();Toast.makeText(this,"Cambios guardados",Toast.LENGTH_SHORT).show();done()}
                else {
                    // Cerrar y actualizar evita repetir una escritura de resultado incierto.
                    dialog.dismiss();status.text=result.exceptionOrNull()?.message ?: "No se pudo confirmar la operación."
                    AlertDialog.Builder(this).setTitle("Revise antes de repetir").setMessage(status.text)
                        .setPositiveButton("Actualizar") {_,_->done()}.setCancelable(false).show()
                }
            }
        }.start()
    }
    private fun participants(record: JSONObject) {
        if(busy) return
        busy=true;enable(root,false);status.text="Consultando participantes…"
        Thread {
            val result=runCatching {JSONObject(rpc("participantes_eclesiastica_app",JSONObject().put("p_registro_id",record.getString("id"))))}
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                busy=false;enable(root,true);render()
                result.fold({status.text="Participantes consultados.";showParticipants(record,it)},{status.text=it.message})
            }
        }.start()
    }
    private fun showParticipants(record: JSONObject, response: JSONObject) {
        val body=layout();val canEdit=record.optBoolean("editable") && record.optString("estado") !in setOf("inactivo","cancelado")
        val people=rows(response.optJSONArray("personas"));val members=rows(response.optJSONArray("participantes"))
        val dialog=AlertDialog.Builder(this).setTitle(record.getString("nombre"))
            .setView(ScrollView(this).apply {addView(body)}).setNegativeButton("Cerrar",null).create()
        label(body,"Solo aparecen personas que puede consultar en este territorio.")
        if(members.isEmpty()) label(body,"No hay participantes visibles.")
        members.forEach {m->
            label(body,"${m.getString("nombre")} · ${txt(m,"rol")}\n${if(m.getBoolean("activo")) "Activo" else "Inactivo"}"+
                if(activityType) " · ${if(m.getBoolean("presente")) "Presente" else "Sin asistencia"}" else "")
            if(canEdit) button(body,"Editar ${m.getString("nombre")}") {dialog.dismiss();participantEditor(record,people,m)}
        }
        if(canEdit && people.isNotEmpty()) button(body,"Agregar participante") {dialog.dismiss();participantEditor(record,people,null)}
        if(!canEdit) label(body,"Consulta únicamente. El registro está cerrado o no tiene permiso de gestión.")
        dialog.show()
    }
    private fun participantEditor(record: JSONObject, people: List<JSONObject>, member: JSONObject?) {
        val form=layout();label(form,"Persona")
        val options=if(member==null) people else listOf(JSONObject().put("id",member.getString("persona_id")).put("nombre",member.getString("nombre")))
        val person=spinner(form,options.map {it.getString("nombre")});person.isEnabled=member==null
        val role=input(form,"Rol / instrumento / responsabilidad",member?.let {txt(it,"rol")}.orEmpty(),120)
        val active=CheckBox(this).apply {text="Participación activa";isChecked=member?.optBoolean("activo") ?: true;form.addView(this)}
        val present=CheckBox(this).apply {text="Asistió";isChecked=member?.optBoolean("presente") ?: false;visibility=if(activityType) View.VISIBLE else View.GONE;form.addView(this)}
        val error=label(form,"")
        val dialog=AlertDialog.Builder(this).setTitle("Participante").setView(ScrollView(this).apply {addView(form)})
            .setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(busy) return@setOnClickListener
            if(activityType && present.isChecked && LocalDate.parse(record.getString("fecha")).isAfter(LocalDate.now())) {
                error.text="No se puede registrar asistencia antes de la actividad.";return@setOnClickListener
            }
            val p=JSONObject().put("p_registro_id",record.getString("id")).put("p_persona_id",options[person.selectedItemPosition].getString("id"))
                .put("p_rol",role.text.toString().trim()).put("p_activo",active.isChecked).put("p_presente",activityType && present.isChecked)
            AlertDialog.Builder(this).setTitle("Confirmar participación").setMessage("${person.selectedItem}\n${if(active.isChecked) "Activo" else "Inactivo"}"+
                if(activityType) "\nAsistió: ${if(present.isChecked) "Sí" else "No"}" else "")
                .setNegativeButton("Volver",null).setPositiveButton("Confirmar") {_,_->save(dialog,form,error,"guardar_participante_app",p) {load()}}.show()
        }}
        dialog.show()
    }
    @Deprecated("Compatibilidad con Activity")
    override fun onBackPressed() { if(!busy) super.onBackPressed() }
}
