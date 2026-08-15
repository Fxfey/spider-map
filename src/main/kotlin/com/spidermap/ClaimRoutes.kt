package com.spidermap

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Error body shape for the whole API, so clients can rely on one format. */
@Serializable
data class ApiError(val error: String)

/**
 * Body of `POST /api/claims`.
 *
 * Separate from [Claim] on purpose: the client supplies only what it is allowed
 * to choose. `id`, `version` and the timestamps are the server's to set, and
 * accepting them from the request would let a client overwrite an unrelated
 * claim by guessing an id.
 */
@Serializable
data class CreateClaimRequest(
    val title: String,
    val world: String,
    val vertices: List<Vertex>,
    @SerialName("owner_uuid") val ownerUuid: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    /**
     * Temporary. From 5.4 this comes from the editor's session and the body
     * value is ignored — a browser must never be able to assert an identity
     * (SDLC §2).
     */
    @SerialName("created_by_uuid") val createdByUuid: String? = null,
)

/**
 * Claim endpoints. Reads are public — anyone with the URL can view the map
 * without logging in (SDLC §2). The write below is unauthenticated only until
 * 5.4 adds session middleware.
 *
 * No validation here beyond "is this parseable": vertex counts, self-
 * intersection, minimum area and overlap each get their own step in Milestone 2.
 */
class ClaimRoutes(private val store: ClaimStore) {

    fun register(routes: RoutesConfig) {
        routes.get("/api/claims") { ctx -> listClaims(ctx) }
        routes.get("/api/claims/{id}") { ctx -> getClaim(ctx) }
        routes.post("/api/claims") { ctx -> createClaim(ctx) }
    }

    private fun listClaims(ctx: Context) {
        ctx.jsonResult(json.encodeToString(CLAIM_LIST, store.load()))
    }

    private fun getClaim(ctx: Context) {
        val id = ctx.pathParam("id")
        val claim = store.load().firstOrNull { it.id == id }

        if (claim == null) {
            ctx.fail(404, "no claim with id '$id'")
            return
        }

        ctx.jsonResult(json.encodeToString(Claim.serializer(), claim))
    }

    private fun createClaim(ctx: Context) {
        val request = try {
            json.decodeFromString(CreateClaimRequest.serializer(), ctx.body())
        } catch (e: SerializationException) {
            // Unparseable or missing a required field. Distinct from the
            // business rules in Milestone 2 — this never reached a claim.
            ctx.fail(400, "malformed request body: ${e.message}")
            return
        }

        val now = timestamp()
        val claim = Claim(
            id = UUID.randomUUID().toString(),
            title = request.title,
            world = request.world,
            ownerUuid = request.ownerUuid,
            ownerName = request.ownerName,
            createdByUuid = request.createdByUuid ?: UNKNOWN_EDITOR,
            vertices = request.vertices,
            version = 1,
            createdAt = now,
            updatedAt = now,
        )

        // Through update() rather than load()+save() so concurrent creates
        // cannot interleave and drop one.
        store.update { existing -> existing + claim }

        ctx.status(201).jsonResult(json.encodeToString(Claim.serializer(), claim))
    }

    private fun Context.jsonResult(body: String): Context =
        contentType("application/json").result(body)

    private fun Context.fail(code: Int, message: String) {
        status(code).jsonResult(json.encodeToString(ApiError.serializer(), ApiError(message)))
    }

    private companion object {

        /** Nil UUID: "an editor made this, but we could not yet say which". */
        const val UNKNOWN_EDITOR = "00000000-0000-0000-0000-000000000000"

        val CLAIM_LIST = ListSerializer(Claim.serializer())

        /** Seconds precision, matching the format in SDLC §4. */
        fun timestamp(): String =
            Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

        /**
         * No prettyPrint, unlike the on-disk format — this is a wire format,
         * and the browser doesn't read the indentation.
         */
        val json = Json {
            encodeDefaults = true
            // A client sending an unexpected field gets a created claim rather
            // than a 400; Milestone 2 is where bodies start being policed.
            ignoreUnknownKeys = true
        }
    }
}
