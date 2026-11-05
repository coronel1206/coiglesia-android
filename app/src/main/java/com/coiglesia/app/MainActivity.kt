package com.coiglesia.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 20)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        val title = TextView(this).apply {
            text = "Coiglesia"
            textSize = 32f
            setTextColor(Color.rgb(25, 51, 86))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Sistema Nacional de Gestión Eclesiástica\nVersión inicial de navegación · Sin datos reales"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 24)
        }
        root.addView(title)
        root.addView(subtitle)
        val detail = TextView(this).apply {
            text = "Selecciona un módulo para explorar la aplicación."
            textSize = 17f
            setPadding(8, 20, 8, 20)
        }
        val scroll = ScrollView(this)
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("Distritos", "Congregaciones", "Personas y miembros", "Pastores", "Comités", "Músicos", "Traslados", "Eventos y asistencia", "Asambleas").forEach { name ->
            val button = Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener { detail.text = "$name\n\nMódulo preparado para la próxima fase. Aún no almacena información." }
            }
            menu.addView(button)
        }
        scroll.addView(menu)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(detail)
        setContentView(root)
    }
}
