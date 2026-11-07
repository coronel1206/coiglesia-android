package com.coiglesia.app.auth

import com.coiglesia.app.network.SupabaseHttp
import org.json.JSONObject

/** Tokens solo en memoria; al morir el proceso se solicita autenticación de nuevo. */
object SessionManager {
    private var initialToken = ""
    private var currentToken = ""
    private var refreshToken = ""
    private var expiresAt = 0L

    @Synchronized fun install(access: String, refresh: String, expiresIn: Long) {
        initialToken = access; currentToken = access; refreshToken = refresh
        expiresAt = System.currentTimeMillis() + expiresIn * 1000
    }
    @Synchronized fun tokenFor(presented: String): String {
        check(initialToken.isNotEmpty()) { "La sesión no está disponible. Vuelva al menú e inicie sesión." }
        check(presented == initialToken || presented == currentToken) { "La sesión cambió. Vuelva al menú e inicie sesión." }
        if (System.currentTimeMillis() < expiresAt - 60000) return currentToken
        check(refreshToken.isNotBlank()) { "La sesión expiró. Inicie sesión nuevamente." }
        val result = JSONObject(SupabaseHttp.request("/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken)))
        val access = result.optString("access_token")
        val refresh = result.optString("refresh_token")
        check(access.isNotBlank() && refresh.isNotBlank()) { "No se pudo renovar la sesión. Inicie sesión nuevamente." }
        currentToken = access; refreshToken = refresh
        expiresAt = System.currentTimeMillis() + result.optLong("expires_in", 3600) * 1000
        return currentToken
    }
    @Synchronized fun clear(): String {
        val previous = currentToken
        initialToken = ""; currentToken = ""; refreshToken = ""; expiresAt = 0
        return previous
    }
}
