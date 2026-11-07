package com.coiglesia.app.reportes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.coiglesia.app.network.SupabaseHttp
import com.coiglesia.app.personas.PersonasActivity
import org.json.JSONObject

class ResumenActivity : Activity() {
    private var csv = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(PersonasActivity.EXTRA_TOKEN).orEmpty()
        if (token.isBlank()) { finish(); return }
        val perfil = intent.getBooleanExtra("perfil", false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(android.graphics.Color.rgb(247,249,252))
        }
        root.addView(TextView(this).apply {
            text = if (perfil) "Mi acceso y responsabilidades" else "Resumen territorial"
            textSize = 25f; setTextColor(android.graphics.Color.rgb(25,51,86))
        })
        val status = TextView(this).apply { text = "Consultando información autorizada…"; textSize=16f }
        root.addView(status)
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(content)
        val refresh = Button(this).apply { text="Actualizar"; isAllCaps=false }
        root.addView(refresh)
        val export = Button(this).apply {
            text="Exportar resumen CSV"; isAllCaps=false; isEnabled=false
            visibility=if (perfil) android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"Coiglesia_resumen_${java.time.LocalDate.now()}.csv")
            },61) }
        }
        root.addView(export)
        root.addView(Button(this).apply { text="Volver"; isAllCaps=false; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        fun card(title: String, body: String) {
            content.addView(TextView(this).apply {
                text="$title\n$body"; textSize=18f; setPadding(16,24,16,24)
            })
        }
        fun load() {
            refresh.isEnabled=false; export.isEnabled=false
            status.text="Consultando información autorizada…"
            Thread {
                val result=runCatching { JSONObject(SupabaseHttp.rpc(
                    if (perfil) "mi_acceso_app" else "resumen_territorial_app",token,JSONObject())) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    refresh.isEnabled=true; content.removeAllViews()
                    result.fold(onSuccess={ data ->
                        if (perfil) {
                            status.text="${data.getString("nombre")} · ${data.getString("estado")}"
                            val cargos=data.getJSONArray("cargos")
                            for (i in 0 until cargos.length()) {
                                val c=cargos.getJSONObject(i)
                                val scope=when(c.getString("nivel")) {
                                    "local"->c.optString("congregacion")
                                    "distrital"->c.optString("distrito")
                                    else->"Organización nacional"
                                }
                                val permissions=c.optJSONArray("permisos")
                                card(c.getString("cargo"),"${c.getString("nivel")} · $scope\nDesde ${c.getString("desde")} hasta ${if(c.isNull("hasta")) "Sin fecha final" else c.getString("hasta")}\n"+
                                    (0 until (permissions?.length() ?: 0)).joinToString("\n") { permissions!!.getString(it) })
                            }
                        } else {
                            status.text="Solo territorios con permisos vigentes. Los movimientos mensuales incluyen ingresos por traslado; no equivalen a nuevos miembros."
                            val rows=mutableListOf(listOf("Indicador","Valor"))
                            listOf("distritos" to "Distritos visibles", "congregaciones" to "Congregaciones visibles",
                                "personas" to "Personas activas registradas", "activas" to "Membresías activas",
                                "finalizadas" to "Membresías finalizadas", "traslados" to "Traslados auditados desde esta versión",
                                "eventos" to "Eventos autorizados", "asambleas" to "Asambleas autorizadas",
                                "asistencias" to "Asistencias registradas").forEach { (key,label)->
                                card(label,data.getLong(key).toString());rows.add(listOf(label,data.getLong(key).toString()))
                            }
                            val groups=data.getJSONArray("por_congregacion")
                            for(i in 0 until groups.length()) {
                                val c=groups.getJSONObject(i)
                                card("${c.getString("distrito")} · ${c.getString("congregacion")}","Activas: ${c.getLong("activas")} · Finalizadas: ${c.getLong("finalizadas")}")
                            }
                            val months=data.getJSONArray("movimientos_mensuales")
                            for(i in 0 until months.length()) {
                                val m=months.getJSONObject(i)
                                card(m.getString("mes"),"Ingresos: ${m.getLong("ingresos")} · Salidas: ${m.getLong("salidas")}")
                            }
                            csv="\uFEFF"+rows.joinToString("\r\n") { row->row.joinToString(",") { "\"${it.replace("\"","\"\"")}\"" } }
                            export.isEnabled=true
                        }
                    },onFailure={status.text=it.message ?: "No se pudo consultar la información"})
                }
            }.start()
        }
        refresh.setOnClickListener { load() }; load()
    }
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==61 && resultCode==RESULT_OK) data?.data?.let { uri ->
            val result=runCatching { contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                ?: error("No se pudo abrir el destino") }
            Toast.makeText(this,if(result.isSuccess) "Resumen guardado" else "No se pudo guardar el resumen",Toast.LENGTH_LONG).show()
        }
    }
}
