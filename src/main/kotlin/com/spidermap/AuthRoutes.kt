package com.spidermap

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Server
import java.time.Duration

/** Body of `POST /api/auth/weblogin` — the code from `/claims weblogin`. */
@Serializable
data class WebLoginRequest(val code: String)

/**
 * What the browser gets back. No UUID: the client has no use for one, and
 * handing it over invites code that sends it back as an identity claim — the
 * exact thing this flow exists to prevent (SDLC §2).
 */
@Serializable
data class WebLoginResponse(
    val token: String,
    @SerialName("player_name") val playerName: String?,
    @SerialName("expires_in_seconds") val expiresInSeconds: Long,
)

/**
 * Exchanges an in-game login code for a browser session.
 *
 * This is the only endpoint that turns "someone who knows a code" into "a
 * Mojang-verified player", so it is the one worth rate-limiting: a six-digit
 * code is a million possibilities, which is nothing to a script (SDLC §2).
 */
class AuthRoutes(
    private val codes: LoginCodeStore,
    private val sessions: SessionStore,
    private val editors: EditorStore,
    private val attempts: RateLimiter,
    private val server: Server,
    private val sessionLifetime: Duration,
) {

    fun register(routes: RoutesConfig) {
        routes.post("/api/auth/weblogin") { ctx -> exchange(ctx) }
    }

    private fun exchange(ctx: Context) {
        val caller = ctx.ip()

        if (!attempts.allow(caller)) {
            ctx.status(429).jsonResult(
                error("too many attempts — wait a few minutes and try again"),
            )
            return
        }

        val request = try {
            json.decodeFromString(WebLoginRequest.serializer(), ctx.body())
        } catch (e: SerializationException) {
            ctx.status(400).jsonResult(error("expected a JSON body of the form {\"code\": \"123456\"}"))
            return
        }

        // consume() spends the code whatever happens next, so a wrong guess
        // cannot be retried and a right one cannot be replayed.
        val playerId = codes.consume(request.code)

        if (playerId == null) {
            // One message for unknown, expired and already-used alike —
            // distinguishing them would confirm which codes exist.
            ctx.status(401).jsonResult(error("that code is not valid — run /claims weblogin again"))
            return
        }

        // Editor rights can be taken away between issuing a code and redeeming
        // it, so this is checked here rather than trusted from the code alone.
        if (!editors.isEditor(playerId)) {
            ctx.status(403).jsonResult(error("you are no longer a map editor"))
            return
        }

        val token = sessions.create(playerId)

        // A legitimate editor who mistyped a couple of times should not spend
        // the rest of the window one typo from a lockout.
        attempts.reset(caller)

        ctx.jsonResult(
            json.encodeToString(
                WebLoginResponse.serializer(),
                WebLoginResponse(
                    token = token,
                    playerName = server.getOfflinePlayer(playerId).name,
                    expiresInSeconds = sessionLifetime.toSeconds(),
                ),
            ),
        )
    }

    private fun error(message: String): String =
        json.encodeToString(ApiError.serializer(), ApiError(message))

    private fun Context.jsonResult(body: String): Context =
        contentType("application/json").result(body)

    private companion object {
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
