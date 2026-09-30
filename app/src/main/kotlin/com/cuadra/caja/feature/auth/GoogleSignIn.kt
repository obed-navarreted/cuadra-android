package com.cuadra.caja.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cuadra.caja.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object NotConfigured : GoogleResult
    data object Cancelled : GoogleResult
    /** El teléfono no tiene ninguna cuenta de Google agregada. */
    data object NoAccount : GoogleResult
    /** Google respondió con un error (no fue cancelar). `detail` = tipo y mensaje de Google, para diagnosticar (p. ej. "[28444] Developer console is not set up correctly"). */
    data class Failed(val detail: String) : GoogleResult
}

/** Pide a Android el ID token de la cuenta de Google. El servidor lo verifica (firma, audiencia, vencimiento) y crea la sesión. */
object GoogleSignIn {
    private const val TAG = "GoogleSignIn"

    suspend fun idToken(activityContext: Context): GoogleResult {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return GoogleResult.NotConfigured
        // Botón explícito "Continuar con Google": la hoja de "Iniciar sesión con Google" muestra las cuentas del teléfono y deja elegir (es la opción que
        // Google recomienda para un botón; `GetGoogleIdOption` es para el inicio automático y devuelve "sin credenciales" con más facilidad).
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleResult.Failed("credencial inesperada: ${credential.type}")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            // Con cuentas en el teléfono esto casi siempre es de configuración (huella SHA-1 de la llave de firma sin cliente Android en Google Cloud).
            Log.w(TAG, "NoCredentialException: ${e.message}")
            GoogleResult.NoAccount
        } catch (e: GetCredentialException) {
            Log.w(TAG, "${e.javaClass.simpleName}: ${e.message}")
            GoogleResult.Failed((e.javaClass.simpleName + (e.message?.let { " – $it" } ?: "")).take(160))
        }
    }
}
