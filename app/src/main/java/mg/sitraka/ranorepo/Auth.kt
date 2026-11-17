package mg.sitraka.ranorepo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object Auth {
    var user by mutableStateOf<FirebaseUser?>(null)
        private set

    fun init() {
        val a = FirebaseAuth.getInstance()
        val cur = a.currentUser
        user = cur
        if (cur != null) Store.startSync(cur.uid)
        a.addAuthStateListener { f ->
            val u = f.currentUser
            user = u
            if (u != null) Store.startSync(u.uid) else Store.stopSync()
        }
    }

    /** Retourne un message d'erreur, ou null si tout s'est bien passé. */
    suspend fun signIn(ctx: Context): String? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(ctx.getString(R.string.default_web_client_id))
                .build()
            val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val res = CredentialManager.create(ctx).getCredential(ctx, req)
            val cred = res.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
                null
            } else {
                "Type d'identifiant non pris en charge"
            }
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: NoCredentialException) {
            "Aucun compte Google trouvé sur cet appareil"
        } catch (e: Exception) {
            "Connexion impossible : ${e.localizedMessage ?: "erreur inconnue"}"
        }
    }

    suspend fun signOut(ctx: Context) {
        Store.stopSync()
        Store.clearLocal()
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
    }
}
