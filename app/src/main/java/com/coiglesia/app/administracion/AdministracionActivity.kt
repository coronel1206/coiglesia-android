package com.coiglesia.app.administracion

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import android.view.View
import com.coiglesia.app.network.SupabaseHttp
import com.coiglesia.app.personas.PersonasActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class AdministracionActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var contenido: LinearLayout
    private lateinit var status: TextView
    private var token=""
    private var busy=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token=intent.getStringExtra(PersonasActivity.EXTRA_TOKEN).orEmpty()
        if(token.isBlank()) { finish();return }
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)
        }
        root.addView(TextView(this).apply {text="Usuarios y cargos";textSize=26f})
        status=TextView(this).apply {textSize=16f};root.addView(status)
        contenido=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};root.addView(contenido)
        root.addView(Button(this).apply {text="Actualizar";isAllCaps=false;setOnClickListener {if(!busy) cargar()} })
        root.addView(Button(this).apply {text="Volver";isAllCaps=false;setOnClickListener {if(!busy) finish()} })
        setContentView(ScrollView(this).apply {addView(root)})
        cargar()
    }
    private fun texto(parent:LinearLayout,s:String) {parent.addView(TextView(this).apply {text=s;textSize=16f;setPadding(0,16,0,8)})}
    private fun options(parent:LinearLayout,title:String,rows:JSONArray,label:(JSONObject)->String):Spinner {
        texto(parent,title)
        return Spinner(this).apply {
            adapter=ArrayAdapter(this@AdministracionActivity,android.R.layout.simple_spinner_dropdown_item,
                listOf("Seleccione…")+(0 until rows.length()).map {label(rows.getJSONObject(it))})
            parent.addView(this)
        }
    }
    private fun campo(parent:LinearLayout,title:String,value:String=""):EditText {
        texto(parent,title);return EditText(this).apply {setText(value);isSingleLine=true;parent.addView(this)}
    }
    private fun cargar() {
        busy=true;status.text="Consultando administración autorizada…";contenido.removeAllViews()
        Thread {
            val result=runCatching {JSONObject(SupabaseHttp.rpc("listar_administracion_app",token,JSONObject()))}
            runOnUiThread {
                if(isFinishing||isDestroyed)return@runOnUiThread
                busy=false
                result.fold(onSuccess={render(it)},onFailure={status.text=it.message})
            }
        }.start()
    }
    private fun ejecutar(title:String,detail:String,rpc:String,payload:JSONObject) {
        if(busy)return
        busy=true
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setNegativeButton("Cancelar"){_,_->busy=false}.setOnCancelListener {busy=false}
            .setPositiveButton("Confirmar"){_,_->
                status.text="Procesando…"
                Thread {
                    val result=runCatching {SupabaseHttp.rpc(rpc,token,payload)}
                    runOnUiThread {
                        if(isFinishing||isDestroyed)return@runOnUiThread
                        busy=false
                        if(result.isSuccess) {
                            Toast.makeText(this,"Cambio guardado y auditado",Toast.LENGTH_LONG).show();cargar()
                        } else status.text="${result.exceptionOrNull()?.message}\nActualice antes de repetir la operación."
                    }
                }.start()
            }.show()
    }
    private fun render(data:JSONObject) {
        status.text="Administración nacional. Los cargos determinan permisos y territorio; cada cambio queda auditado."
        val users=data.getJSONArray("usuarios")
        texto(contenido,"Usuarios vinculados")
        for(i in 0 until users.length()) {
            val u=users.getJSONObject(i)
            texto(contenido,"${u.getString("nombre")} · ${u.getString("estado")}")
            contenido.addView(Button(this).apply {
                text="Cambiar estado de acceso";isAllCaps=false
                setOnClickListener {
                    if(!busy) {
                        val states=arrayOf("activo","suspendido","bloqueado","pendiente")
                        AlertDialog.Builder(this@AdministracionActivity).setTitle(u.getString("nombre"))
                            .setItems(states){_,position->ejecutar("Cambiar estado", "${u.getString("nombre")} → ${states[position]}",
                                "cambiar_estado_usuario_app",JSONObject().put("p_usuario_id",u.getString("id")).put("p_estado",states[position]))}.show()
                    }
                }
            })
        }
        texto(contenido,"Asignar un cargo")
        val personas=data.getJSONArray("personas");val cargos=data.getJSONArray("cargos")
        val distritos=data.getJSONArray("distritos");val congregaciones=data.getJSONArray("congregaciones")
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;visibility=View.GONE}
        contenido.addView(Button(this).apply {
            text="Nueva asignación";isAllCaps=false
            setOnClickListener {if(!busy)panel.visibility=if(panel.visibility==View.VISIBLE)View.GONE else View.VISIBLE}
        })
        contenido.addView(panel)
        val persona=options(panel,"Persona",personas){it.getString("nombre")}
        val cargo=options(panel,"Cargo",cargos){"${it.getString("nombre")} · ${it.getString("nivel")}"}
        texto(panel,"Nacional: sin territorio. Distrital: seleccione distrito. Local: seleccione congregación.")
        val distrito=options(panel,"Distrito (solo cargos distritales)",distritos){it.getString("nombre")}
        val congregacion=options(panel,"Congregación (solo cargos locales)",congregaciones){c->
            val d=(0 until distritos.length()).map {distritos.getJSONObject(it)}.find {it.getString("id")==c.getString("distrito_id")}
            "${d?.getString("nombre").orEmpty()} · ${c.getString("nombre")}"
        }
        val inicio=campo(panel,"Fecha de inicio · AAAA-MM-DD",LocalDate.now().toString())
        val fin=campo(panel,"Fecha final opcional · AAAA-MM-DD")
        val notas=campo(panel,"Observaciones")
        panel.addView(Button(this).apply {
            text="Guardar asignación";isAllCaps=false
            setOnClickListener {
                val p=personas.optJSONObject(persona.selectedItemPosition-1)
                val c=cargos.optJSONObject(cargo.selectedItemPosition-1)
                if(p==null||c==null){status.text="Seleccione persona y cargo";return@setOnClickListener}
                val desde=inicio.text.toString().trim();val hasta=fin.text.toString().trim()
                if(runCatching {LocalDate.parse(desde);if(hasta.isNotEmpty())LocalDate.parse(hasta)}.isFailure) {
                    status.text="Revise las fechas (AAAA-MM-DD)";return@setOnClickListener
                }
                val level=c.getString("nivel")
                val d=if(level=="distrital")distritos.optJSONObject(distrito.selectedItemPosition-1) else null
                val cg=if(level=="local")congregaciones.optJSONObject(congregacion.selectedItemPosition-1) else null
                if((level=="distrital"&&d==null)||(level=="local"&&cg==null)) {status.text="Seleccione el territorio del cargo";return@setOnClickListener}
                ejecutar("Asignar cargo", "${p.getString("nombre")}\n${c.getString("nombre")}\n${d?.getString("nombre") ?: cg?.getString("nombre") ?: "Nacional"}\nVigencia: $desde → ${hasta.ifBlank {"Sin fecha final"}}",
                    "asignar_cargo_app",JSONObject().put("p_persona_id",p.getString("id")).put("p_cargo_id",c.getString("id"))
                        .put("p_distrito_id",d?.getString("id") ?: JSONObject.NULL)
                        .put("p_congregacion_id",cg?.getString("id") ?: JSONObject.NULL)
                        .put("p_fecha_inicio",desde).put("p_fecha_fin",hasta.ifBlank {JSONObject.NULL})
                        .put("p_observaciones",notas.text.toString().trim().ifBlank {JSONObject.NULL}))
            }
        })
        texto(contenido,"Cargos y permisos establecidos")
        for(i in 0 until cargos.length()) {
            val c=cargos.getJSONObject(i);val permisos=c.optJSONArray("permisos")
            texto(contenido,"${c.getString("nombre")} · ${c.getString("nivel")}\n"+(0 until (permisos?.length() ?: 0)).joinToString(" · "){permisos!!.getString(it)})
            contenido.addView(Button(this).apply {
                text="Revisar y editar permisos";isAllCaps=false
                setOnClickListener {
                    if(!busy) {
                        busy=true
                        EditorPermisos.mostrar(this@AdministracionActivity,token,c.getString("id"),c.getString("nombre"),c.getString("nivel")) {
                            busy=false
                            if(!isFinishing && !isDestroyed) cargar()
                        }
                    }
                }
            })
        }
        texto(contenido,"Asignaciones e historial")
        val rows=data.getJSONArray("asignaciones")
        for(i in 0 until rows.length()) {
            val a=rows.getJSONObject(i)
            texto(contenido,"${a.getString("persona")} · ${a.getString("cargo")}\n${a.getString("estado")} · Desde ${a.getString("desde")}\n"+
                when(a.getString("nivel")){"local"->a.optString("congregacion");"distrital"->a.optString("distrito");else->"Nacional"})
            if(a.getString("estado")!="finalizado")contenido.addView(Button(this).apply {
                text="Finalizar asignación";isAllCaps=false
                setOnClickListener {ejecutar("Finalizar cargo","${a.getString("persona")} · ${a.getString("cargo")}\nSe finalizará con fecha de hoy y se conservará el historial.",
                    "finalizar_cargo_app",JSONObject().put("p_asignacion_id",a.getString("id")).put("p_fecha_fin",LocalDate.now().toString()))}
            })
        }
    }
    @Deprecated("Activity back compatibility")
    override fun onBackPressed(){if(!busy)super.onBackPressed()}
}
