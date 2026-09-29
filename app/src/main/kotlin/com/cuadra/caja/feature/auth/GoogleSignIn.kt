package com.cuadra.caja.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cuadra.caja.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object NotConfigured : GoogleResult
    data object Cancelled : GoogleResult
    /** El teléfono no tiene ninguna cuenta de Google agregada. */
    data object NoAccount : GoogleResult
    data object Failed : GoogleResult
}

/** Pide a Android el ID token de la cuenta de Google. El servidor lo verifica (firma, audiencia, vencimiento) y crea la sesión. */
object GoogleSignIn {
    suspend fun idToken(activityContext: Context): GoogleResult {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return GoogleResult.NotConfigured
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleResult.Failed
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleResult.NoAccount
        } catch (_: GetCredentialException) {
            GoogleResult.Failed
        }
    }
}
