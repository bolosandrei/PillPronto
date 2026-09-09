package com.pillpronto.ui.account

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

/** Rezultat tipizat al fluxului Credential Manager — separat de eroarea reala, ca sa nu aratam
 * un mesaj de eroare cand userul doar a inchis dialogul de alegere a contului (la fel ca esecul
 * tacut de la scanarea QR din MyPatientsScreen.kt). */
sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String, val rawNonce: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failed(val message: String?) : GoogleSignInOutcome
}

/** Google Sign-In nativ (Credential Manager), NU WebView/Custom Tabs OAuth. Functie simpla in
 * stratul ui (nu trece prin Hilt/domain) — CredentialManager cere Context de Activity, la fel ca
 * scanInviteQrCode (GmsBarcodeScanning) din MyPatientsScreen.kt. Nonce-ul (random + hash SHA-256)
 * e cerut de validarea implicita a Supabase Auth impotriva replay attacks. */
suspend fun requestGoogleSignIn(context: Context, webClientId: String): GoogleSignInOutcome {
    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce = MessageDigest.getInstance("SHA-256")
        .digest(rawNonce.toByteArray())
        .fold("") { acc, byte -> acc + "%02x".format(byte) }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setNonce(hashedNonce)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = CredentialManager.create(context).getCredential(context = context, request = request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        GoogleSignInOutcome.Success(idToken = credential.idToken, rawNonce = rawNonce)
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInOutcome.Cancelled
    } catch (e: GetCredentialException) {
        Log.e("GoogleSignInHelper", "Credential Manager a esuat", e)
        GoogleSignInOutcome.Failed(e.message)
    } catch (e: GoogleIdTokenParsingException) {
        Log.e("GoogleSignInHelper", "Nu am putut parsa token-ul Google", e)
        GoogleSignInOutcome.Failed(e.message)
    }
}
