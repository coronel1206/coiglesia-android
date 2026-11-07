package com.coiglesia.app.administracion

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.*
import com.coiglesia.app.network.SupabaseHttp
import org.json.JSONArray
import org.json.JSONObject

/** El catálogo viene del servidor; ninguna casilla inventa o concede códigos por defecto. */
object EditorPermisos {
    fun mostrar(activity: Activity, token: String, cargoId: String, nombre: String, nivel: String, terminado: () -> Unit) {
        val body=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)
        }
        fun label(value:String)=TextView(activity).apply {text=value;textSize=16f;setPadding(0,12,0,12);body.addView(this)}
        label("$nombre · $nivel")
        label("Los cambios afectan a todos los usuarios que tengan este cargo, dentro de sus territorios y vigencias. Otro administrador debe modificar los permisos de sus propios cargos vigentes.")
        val status=label("Consultando catálogo de permisos…")
        val choices=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;body.addView(this)}
        val dialog=AlertDialog.Builder(activity).setTitle("Permisos del cargo")
            .setView(ScrollView(activity).apply {addView(body)})
            .setPositiveButton("Revisar cambios",null).setNegativeButton("Cerrar",null).create()
        var saving=false
        var original=setOf<String>()
        val boxes=linkedMapOf<String,CheckBox>()
        val descriptions=mutableMapOf<String,String>()
        dialog.setOnDismissListener {terminado()}
        dialog.setOnShowListener {
            val save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.isEnabled=false
            val close=dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            Thread {
                val result=runCatching {JSONArray(SupabaseHttp.rpc("permisos_cargo_app",token,JSONObject().put("p_cargo_id",cargoId)))}
                activity.runOnUiThread {
                    if(activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                    result.fold(onSuccess={rows->
                        val selected=mutableSetOf<String>()
                        for(i in 0 until rows.length()) {
                            val row=rows.getJSONObject(i);val code=row.getString("codigo")
                            if(row.getBoolean("asignado")) selected.add(code)
                            descriptions[code]=row.getString("descripcion")
                            boxes[code]=CheckBox(activity).apply {
                                text="${row.getString("descripcion")}\n$code";isChecked=row.getBoolean("asignado")
                                // Backend verifica de nuevo; una selección preexistente puede retirarse.
                                if(code=="usuarios.administrar" && nivel!="nacional") {
                                    isEnabled=isChecked
                                    setOnCheckedChangeListener {button,checked->if(!checked) button.isEnabled=false}
                                }
                                choices.addView(this)
                            }
                        }
                        original=selected.toSet();save.isEnabled=boxes.isNotEmpty()
                        status.text="Marque los permisos necesarios. Actividades abarca todos los equipos del territorio; todavía no limita por departamento."
                    },onFailure={status.text=it.message ?: "No se pudo consultar el catálogo."})
                }
            }.start()
            save.setOnClickListener {
                if(saving) return@setOnClickListener
                val selected=boxes.filterValues {it.isChecked}.keys.toSet()
                val changes=CambiosPermisos.comparar(original,selected)
                if(changes.sinCambios) {status.text="No hay cambios por guardar.";return@setOnClickListener}
                fun names(codes:List<String>)=if(codes.isEmpty()) "Ninguno" else codes.joinToString("\n") {"• ${descriptions[it] ?: it} ($it)"}
                val detail="Cargo: $nombre\nNivel: $nivel\nSe aplica a todos sus titulares.\n\nAñadir:\n${names(changes.agregados)}\n\nRetirar:\n${names(changes.retirados)}"+
                    if(selected.isEmpty()) "\n\nEl cargo quedará sin permisos. Sus titulares conservarán únicamente los accesos que otorguen otros cargos vigentes." else ""
                AlertDialog.Builder(activity).setTitle("Confirmar permisos").setMessage(detail)
                    .setNegativeButton("Volver",null).setPositiveButton("Guardar cambios") {_,_->
                        if(saving) return@setPositiveButton
                        saving=true;save.isEnabled=false;close.isEnabled=false;dialog.setCancelable(false)
                        boxes.values.forEach {it.isEnabled=false};status.text="Guardando y verificando cambios…"
                        val params=JSONObject().put("p_cargo_id",cargoId)
                            .put("p_codigos",JSONArray(selected.sorted())).put("p_codigos_esperados",JSONArray(original.sorted()))
                        Thread {
                            val result=runCatching {SupabaseHttp.rpc("configurar_permisos_cargo_verificado_app",token,params)}
                            activity.runOnUiThread {
                                if(activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                                saving=false
                                if(result.isSuccess) {
                                    Toast.makeText(activity,"Permisos guardados y auditados",Toast.LENGTH_LONG).show();dialog.dismiss()
                                } else {
                                    // No se reenvía ni se usa la firma antigua si falta la nueva RPC.
                                    status.text="${result.exceptionOrNull()?.message}\nCierre y vuelva a abrir el cargo para comprobar el resultado antes de repetir. Si la nueva función no está instalada, aplique primero el SQL de COG-003."
                                    save.visibility=View.GONE;close.isEnabled=true;dialog.setCancelable(true)
                                }
                            }
                        }.start()
                    }.show()
            }
        }
        dialog.show()
    }
}
