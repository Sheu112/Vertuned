package app.vertune.desktop.discord

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Logs in to Discord the way Discord's own desktop and web apps do with a QR code: the Mac
 * shows a code, you scan it with the Discord app on your phone and confirm, and Discord hands
 * this app the account token (encrypted with a key that never leaves this Mac).
 * The phone app does the same job with Discord's login page in a web view; Compose for Mac
 * has no web view, so this is the closest equivalent.
 */
object DiscordQrLogin {
    sealed interface Step {
        data object Connecting : Step
        /** Show [url] as a QR code. */
        data class ShowCode(val url: String) : Step
        /** Scanned by [username]; waiting for them to confirm on the phone. */
        data class Scanned(val username: String) : Step
        data class Done(val token: String) : Step
        data class Failed(val reason: String) : Step
    }

    private val client by lazy { HttpClient(OkHttp) { install(WebSockets) } }

    /** Runs the whole login, reporting each step; returns when it's done, failed or cancelled. */
    suspend fun run(onStep: (Step) -> Unit) {
        onStep(Step.Connecting)
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        var finished = false
        try {
            client.webSocket(GATEWAY_URL, request = {
                header("Origin", "https://discord.com")
                header("User-Agent", USER_AGENT)
            }) {
                coroutineScope {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = JSONObject(frame.readText())
                        when (msg.optString("op")) {
                            "hello" -> {
                                val interval = msg.optLong("heartbeat_interval", DEFAULT_HEARTBEAT_MS)
                                launch {
                                    while (isActive) {
                                        delay(interval)
                                        sendJson(JSONObject().put("op", "heartbeat"))
                                    }
                                }
                                val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
                                sendJson(JSONObject().put("op", "init").put("encoded_public_key", publicKey))
                            }
                            "nonce_proof" -> {
                                val nonce = decrypt(keys.private, msg.getString("encrypted_nonce"))
                                val proof = Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(nonce))
                                sendJson(JSONObject().put("op", "nonce_proof").put("proof", proof))
                            }
                            "pending_remote_init" -> onStep(Step.ShowCode(CODE_URL + msg.getString("fingerprint")))
                            "pending_ticket" -> {
                                // "id:discriminator:avatar:username"
                                val user = String(decrypt(keys.private, msg.getString("encrypted_user_payload")))
                                onStep(Step.Scanned(user.split(':').getOrNull(3).orEmpty()))
                            }
                            "pending_login" -> {
                                val token = exchangeTicket(msg.getString("ticket"), keys.private)
                                finished = true
                                onStep(if (token != null) Step.Done(token) else Step.Failed("Discord didn't hand over the login. Try again, or paste the token instead."))
                                break
                            }
                            "cancel" -> {
                                finished = true
                                onStep(Step.Failed("The login was cancelled on the phone."))
                                break
                            }
                        }
                    }
                    coroutineContext.cancelChildren()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!finished) onStep(Step.Failed("Couldn't reach Discord: ${e.message}"))
            return
        }
        // Discord closes the code after a couple of minutes.
        if (!finished) onStep(Step.Failed("The code expired. Try again."))
    }

    private suspend fun exchangeTicket(ticket: String, key: PrivateKey): String? = runCatching {
        val response = client.post(LOGIN_URL) {
            header("User-Agent", USER_AGENT)
            header("Origin", "https://discord.com")
            contentType(ContentType.Application.Json)
            setBody(JSONObject().put("ticket", ticket).toString())
        }
        if (!response.status.isSuccess()) {
            println("[DiscordQrLogin] login failed: ${response.status}")
            return null
        }
        String(decrypt(key, JSONObject(response.bodyAsText()).getString("encrypted_token")))
    }.onFailure { println("[DiscordQrLogin] login failed: $it") }.getOrNull()

    private fun decrypt(key: PrivateKey, base64: String): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, key,
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        )
        return cipher.doFinal(Base64.getDecoder().decode(base64))
    }

    private suspend fun DefaultClientWebSocketSession.sendJson(json: JSONObject) = send(Frame.Text(json.toString()))

    private fun kotlin.coroutines.CoroutineContext.cancelChildren() =
        this[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }

    private const val GATEWAY_URL = "wss://remote-auth-gateway.discord.gg/?v=2"
    private const val LOGIN_URL = "https://discord.com/api/v9/users/@me/remote-auth/login"
    private const val CODE_URL = "https://discord.com/ra/"
    private const val KEY_BITS = 2048
    private const val DEFAULT_HEARTBEAT_MS = 41_250L
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
