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
 * Body of `PUT /api/claims/{id}`.
 *
 * `world` is absent deliberately: SDLC §5 allows updating "title, owner, and/or
 * vertices", and moving a claim between dimensions is a different operation
 * from editing one.
 */
@Serializable
data class UpdateClaimRequest(
    val title: String,
    val vertices: List<Vertex>,
    /**
     * The version the client loaded. Required — a client that cannot say what
     * it was editing cannot be allowed to overwrite someone else's edit.
     */
    val version: Int,
    @SerialName("owner_uuid") val ownerUuid: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
)

/**
 * Claim endpoints. Reads are public — anyone with the URL can view the map
 * without logging in (SDLC §2). The writes below are unauthenticated only until
 * 5.4 adds session middleware.
 *
 * No validation here beyond "is this parseable": vertex counts, self-
 * intersection, minimum area and overlap each get their own step in Milestone 2.
 */
class ClaimRoutes(
    private val store: ClaimStore,
    private val validator: ClaimValidator,
) {

    fun register(routes: RoutesConfig) {
        routes.get("/api/claims") { ctx -> listClaims(ctx) }
        routes.get("/api/claims/{id}") { ctx -> getClaim(ctx) }
        routes.post("/api/claims") { ctx -> createClaim(ctx) }
        routes.put("/api/claims/{id}") { ctx -> updateClaim(ctx) }
        routes.delete("/api/claims/{id}") { ctx -> deleteClaim(ctx) }
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

        validator.validate(request.vertices)?.let { problem ->
            ctx.fail(400, problem)
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

    private fun updateClaim(ctx: Context) {
        val id = ctx.pathParam("id")

        val request = try {
            json.decodeFromString(UpdateClaimRequest.serializer(), ctx.body())
        } catch (e: SerializationException) {
            ctx.fail(400, "malformed request body: ${e.message}")
            return
        }

        // Checked before taking the lock: a bad shape is bad regardless of what
        // is currently stored, so there is no reason to hold up other writers.
        validator.validate(request.vertices)?.let { problem ->
            ctx.fail(400, problem)
            return
        }

        // The lookup, the version check and the write have to happen under one
        // lock, or two editors could both read version 3 and both save version
        // 4 — which is the exact race the version field exists to stop.
        var rejection: Pair<Int, String>? = null
        var saved: Claim? = null

        store.update { claims ->
            val existing = claims.firstOrNull { it.id == id }

            when {
                existing == null -> {
                    rejection = 404 to "no claim with id '$id'"
                    claims
                }

                existing.version != request.version -> {
                    rejection = 409 to
                        "claim changed since you loaded it " +
                        "(you have version ${request.version}, current is ${existing.version}) " +
                        "— reload and retry"
                    claims
                }

                else -> {
                    val next = existing.copy(
                        title = request.title,
                        ownerUuid = request.ownerUuid,
                        ownerName = request.ownerName,
                        vertices = request.vertices,
                        version = existing.version + 1,
                        updatedAt = timestamp(),
                    )
                    saved = next
                    claims.map { if (it.id == id) next else it }
                }
            }
        }

        rejection?.let { (code, message) ->
            ctx.fail(code, message)
            return
        }

        ctx.jsonResult(json.encodeToString(Claim.serializer(), saved!!))
    }

    private fun deleteClaim(ctx: Context) {
        val id = ctx.pathParam("id")
        var existed = false

        store.update { claims ->
            val remaining = claims.filterNot { it.id == id }
            existed = remaining.size != claims.size
            if (existed) remaining else claims
        }

        if (!existed) {
            ctx.fail(404, "no claim with id '$id'")
            return
        }

        // 204: succeeded, and there is deliberately nothing to return.
        ctx.status(204)
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
