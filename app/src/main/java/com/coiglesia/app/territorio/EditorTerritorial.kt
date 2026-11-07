package com.coiglesia.app.territorio

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.*
import com.coiglesia.app.network.SupabaseHttp
import org.json.JSONObject

/** Edición sin borrado; el servidor verifica permisos y dependencias territoriales. */
object EditorTerritorial {
    data class Campo(val parametro: String, val etiqueta: String, val valor: String, val limite: Int)

    fun mostrar(activity: Activity, token: String, titulo: String, funcion: String,
                id: String, campos: List<Campo>, estados: List<String>, estadoActual: String,
                actualizado: () -> Unit) {
        val contenido = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val entradas = campos.map { campo ->
            contenido.addView(TextView(activity).apply { text = campo.etiqueta })
            EditText(activity).apply {
                setText(campo.valor)
                filters = arrayOf(InputFilter.LengthFilter(campo.limite))
                contenido.addView(this)
            }
        }
        contenido.addView(TextView(activity).apply { text = "Estado" })
        val estado = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, estados)
            setSelection(estados.indexOf(estadoActual).coerceAtLeast(0))
            contenido.addView(this)
        }
        val mensaje = TextView(activity).also { contenido.addView(it) }
        val dialogo = AlertDialog.Builder(activity).setTitle(titulo)
            .setView(ScrollView(activity).apply { addView(contenido) })
            .setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create()
        var ocupado = false
        dialogo.setOnShowListener {
            val guardar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE)
            guardar.setOnClickListener {
                if (ocupado) return@setOnClickListener
                val parametros = JSONObject().put("p_id", id)
                campos.zip(entradas).forEach { (campo, entrada) ->
                    parametros.put(campo.parametro, entrada.text.toString().trim())
                }
                if (parametros.optString("p_codigo").isBlank() || parametros.optString("p_nombre").isBlank()) {
                    mensaje.text = "Código y nombre son obligatorios."
                    return@setOnClickListener
                }
                val seleccionado = estados[estado.selectedItemPosition]
                parametros.put("p_estado", seleccionado)
                fun enviar() {
                    if (ocupado) return
                    ocupado = true
                    guardar.isEnabled = false; cancelar.isEnabled = false
                    entradas.forEach { it.isEnabled = false }; estado.isEnabled = false
                    dialogo.setCancelable(false)
                    mensaje.text = "Guardando cambios…"
                    Thread {
                        val resultado = runCatching { SupabaseHttp.rpc(funcion, token, parametros) }
                        activity.runOnUiThread {
                            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                            if (resultado.isSuccess) {
                                dialogo.dismiss()
                                Toast.makeText(activity, "Cambios guardados", Toast.LENGTH_SHORT).show()
                                actualizado()
                            } else {
                                ocupado = false
                                guardar.isEnabled = true; cancelar.isEnabled = true
                                entradas.forEach { it.isEnabled = true }; estado.isEnabled = true
                                dialogo.setCancelable(true)
                                mensaje.text = resultado.exceptionOrNull()?.message ?: "No se pudo guardar."
                            }
                        }
                    }.start()
                }
                if (seleccionado != estadoActual) {
                    AlertDialog.Builder(activity).setTitle("Confirmar cambio de estado")
                        .setMessage("Cambiar de $estadoActual a $seleccionado. El servidor verificará los registros vinculados.")
                        .setPositiveButton("Confirmar") { _, _ -> enviar() }
                        .setNegativeButton("Cancelar", null).show()
                } else enviar()
            }
        }
        dialogo.show()
    }
}
