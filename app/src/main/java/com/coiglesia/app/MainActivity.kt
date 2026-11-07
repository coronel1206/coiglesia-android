package com.coiglesia.app

import com.coiglesia.app.notificaciones.NotificacionesActivity
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.coiglesia.app.auth.SupabaseAuth
import com.coiglesia.app.auth.AccessVerifier
import com.coiglesia.app.distritos.DistritosActivity
import com.coiglesia.app.congregaciones.CongregacionesActivity
import com.coiglesia.app.obras.ObrasActivity
import com.coiglesia.app.personas.PersonasActivity

class MainActivity : Activity() {

    private var sessionToken: String? = null

    private val modules = listOf(
        "Resumen territorial",
        "Mi acceso y responsabilidades",
        "Usuarios y cargos",
        "Distritos",
        "Congregaciones",
        "Obras en Proyección",
        "Notificaciones Distritales",
        "Personas y miembros",
        "Pastores",
        "Ministerios",
        "Comités",
        "Músicos",
        "Traslados",
        "Eventos y asistencia",
        "Asambleas"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    private fun createRoot(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt(), (20 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt())
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
    }

    private fun showLogin() {
        val root = createRoot()

        val title = TextView(this).apply {
            text = "Coiglesia Nacional"
            textSize = 30f
            setTextColor(Color.rgb(25, 51, 86))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Sistema Nacional de Gestión Eclesiástica"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }

        val email = EditText(this).apply {
            hint = "Correo electrónico"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
        }

        val password = EditText(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

        val status = TextView(this).apply {
            text = "Ingresa con tu cuenta autorizada."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val loginButton = Button(this).apply {
            text = "Iniciar sesión"
            isAllCaps = false
        }

        loginButton.setOnClickListener {
            val enteredEmail = email.text.toString().trim()
            val enteredPassword = password.text.toString()

            if (enteredEmail.isBlank() || enteredPassword.isBlank()) {
                status.text = "Ingresa el correo y la contraseña."
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            status.text = "Verificando credenciales y permisos..."

            Thread {
                val login = SupabaseAuth.login(
                    enteredEmail,
                    enteredPassword
                )

                val access = if (
                    login.success &&
                    !login.accessToken.isNullOrBlank()
                ) {
                    AccessVerifier.verify(login.accessToken)
                } else {
                    AccessVerifier.AccessResult(
                        false,
                        login.message
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    if (access.authorized) {
                        com.coiglesia.app.auth.SessionManager.install(login.accessToken!!, login.refreshToken, login.expiresIn)
                        password.text.clear()
                        sessionToken = login.accessToken
                        showMainMenu()
                    } else {
                        status.text = access.message
                        loginButton.isEnabled = true
                    }
                }
            }.start()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(email)
        root.addView(password)
        root.addView(loginButton)
        root.addView(status)

        setContentView(root)
    }

    private fun showMainMenu() {
        val root = createRoot()

        val title = TextView(this).apply {
            text = "Coiglesia"
            textSize = 32f
            setTextColor(Color.rgb(25, 51, 86))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Sistema Nacional de Gestión Eclesiástica\n" +
                "Acceso según sus permisos y territorio"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 24)
        }

        val detail = TextView(this).apply {
            text = "Seleccione un módulo. Cada operación valida sus permisos."
            textSize = 17f
            setPadding(8, 20, 8, 20)
        }

        val scroll = ScrollView(this)

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        modules.forEach { name ->
            val button = Button(this).apply {
                val pendiente = name in setOf("Pastores")
                text = if (pendiente) "$name · pendiente" else name
                isEnabled = !pendiente
                isAllCaps = false
                setOnClickListener {
                    val tipoEquipo = mapOf("Ministerios" to "ministerio", "Comités" to "comite", "Músicos" to "musica", "Eventos y asistencia" to "evento", "Asambleas" to "asamblea")[name]
                    if (tipoEquipo != null) {
                        val token = sessionToken
                        if (token.isNullOrBlank()) showLogin()
                        else startActivity(Intent(this@MainActivity, com.coiglesia.app.eclesiastica.EclesiasticaActivity::class.java)
                            .putExtra("access_token", token).putExtra("tipo", tipoEquipo))
                    } else if (name == "Resumen territorial" || name == "Mi acceso y responsabilidades") {
                        val token = sessionToken
                        if (token.isNullOrBlank()) showLogin()
                        else startActivity(Intent(this@MainActivity,
                            com.coiglesia.app.reportes.ResumenActivity::class.java)
                            .putExtra(PersonasActivity.EXTRA_TOKEN, token)
                            .putExtra("perfil", name == "Mi acceso y responsabilidades"))
                    } else if (name == "Usuarios y cargos") {
                        val token = sessionToken
                        if (token.isNullOrBlank()) showLogin()
                        else startActivity(Intent(this@MainActivity,
                            com.coiglesia.app.administracion.AdministracionActivity::class.java)
                            .putExtra(PersonasActivity.EXTRA_TOKEN, token))
                    } else if (name == "Distritos") {
                        val token = sessionToken
                        if (token.isNullOrBlank()) {
                            showLogin()
                        } else {
                            val intent = Intent(
                                this@MainActivity,
                                DistritosActivity::class.java
                            )
                            intent.putExtra(
                                DistritosActivity.EXTRA_TOKEN,
                                token
                            )
                            startActivity(intent)
                        }
                    } else if (name == "Congregaciones") {
                        val token = sessionToken

                        if (token.isNullOrBlank()) {
                            showLogin()
                        } else {
                            val intent = Intent(
                                this@MainActivity,
                                CongregacionesActivity::class.java
                            )
                            intent.putExtra(
                                CongregacionesActivity.EXTRA_TOKEN,
                                token
                            )
                            startActivity(intent)
                        }
                    } else if (name == "Obras en Proyección") {
                        val token = sessionToken

                        if (token.isNullOrBlank()) {
                            showLogin()
                        } else {
                            val intent = Intent(
                                this@MainActivity,
                                ObrasActivity::class.java
                            )
                            intent.putExtra(
                                ObrasActivity.EXTRA_TOKEN,
                                token
                            )
                            startActivity(intent)
                        }
                    } else if (name == "Notificaciones Distritales") {
                        val token = sessionToken
                        if (token.isNullOrBlank()) {
                            showLogin()
                        } else {
                            val intent = Intent(
                                this@MainActivity,
                                NotificacionesActivity::class.java
                            )
                            intent.putExtra(
                                NotificacionesActivity.EXTRA_TOKEN,
                                token
                            )
                            startActivity(intent)
                        }
                    } else if (name == "Traslados") {
                        val token = sessionToken
                        if (token.isNullOrBlank()) showLogin()
                        else startActivity(Intent(this@MainActivity,
                            com.coiglesia.app.personas.MembresiasActivity::class.java)
                            .putExtra(PersonasActivity.EXTRA_TOKEN, token))
                    } else if (name == "Personas y miembros") {
                        val token = sessionToken

                        if (token.isNullOrBlank()) {
                            showLogin()
                        } else {
                            val intent = Intent(
                                this@MainActivity,
                                PersonasActivity::class.java
                            )
                            intent.putExtra(
                                PersonasActivity.EXTRA_TOKEN,
                                token
                            )
                            startActivity(intent)
                        }
                    } else {
                        detail.text = "$name\n\n" +
                            "Módulo preparado para la próxima fase. " +
                            "Aún no almacena información."
                    }
                }
            }
            menu.addView(button, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            })
        }

        scroll.addView(menu)

        val logoutButton = Button(this).apply {
            text = "Cerrar sesión"
            isAllCaps = false
            setOnClickListener {
                val previous = com.coiglesia.app.auth.SessionManager.clear()
                Thread {
                    if (previous.isNotBlank()) runCatching {
                        com.coiglesia.app.network.SupabaseHttp.request("/auth/v1/logout?scope=local", org.json.JSONObject(), previous)
                    }
                }.start()
                sessionToken = null
                showLogin()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(detail)
        root.addView(logoutButton)

        setContentView(root)
    }
}
