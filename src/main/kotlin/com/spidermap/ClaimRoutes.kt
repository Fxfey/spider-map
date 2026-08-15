package com.spidermap

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Error body shape for the whole API, so clients can rely on one format. */
@Serializable
data class ApiError(val error: String)

/**
 * Read endpoints for claims. Both are public — anyone with the URL can view the
 * map without logging in (SDLC §2); only writes need an editor session.
 */
class ClaimRoutes(private val store: ClaimStore) {

    fun register(routes: RoutesConfig) {
        routes.get("/api/claims") { ctx -> listClaims(ctx) }
        routes.get("/api/claims/{id}") { ctx -> getClaim(ctx) }
    }

    private fun listClaims(ctx: Context) {
        ctx.jsonResult(json.encodeToString(CLAIM_LIST, store.load()))
    }

    private fun getClaim(ctx: Context) {
        val id = ctx.pathParam("id")
        val claim = store.load().firstOrNull { it.id == id }

        if (claim == null) {
            ctx.status(404).jsonResult(json.encodeToString(ApiError.serializer(), ApiError("no claim with id '$id'")))
            return
        }

        ctx.jsonResult(json.encodeToString(Claim.serializer(), claim))
    }

    /**
     * Serialising here rather than via ctx.json(): Javalin's default mapper is
     * Jackson, which we deliberately don't ship, and kotlinx's generated
     * serializers are resolved at compile time instead of reflectively.
     */
    private fun Context.jsonResult(body: String): Context =
        contentType("application/json").result(body)

    private companion object {

        val CLAIM_LIST = ListSerializer(Claim.serializer())

        /**
         * No prettyPrint, unlike the on-disk format — this is a wire format,
         * and the browser doesn't read the indentation.
         */
        val json = Json {
            encodeDefaults = true
        }
    }
}
