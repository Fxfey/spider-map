import { useEffect, useRef, useState } from 'react'
import { MapContainer, Polygon, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import { CRS } from 'leaflet'
import ChunkGrid from './ChunkGrid'
import ClaimDrawer from './ClaimDrawer'
import { fromLatLng, roundPoint, toLatLng, type WorldPoint } from '../coords'
import { fetchClaims, WORLDS, type Claim, type WorldId } from '../api'

/**
 * Keeps Leaflet's idea of the container size in step with reality.
 *
 * Leaflet measures the container once at construction. If the map mounts before
 * layout settles — a hidden tab, a slow font, a resized window — it keeps the
 * stale size, and every screen-to-world conversion is offset by the difference.
 * The symptom is subtle: the map works, but the centre isn't where you asked
 * for and the cursor readout is wrong by a fixed amount.
 */
function KeepSizeInSync() {
  const map = useMap()

  useEffect(() => {
    map.invalidateSize()

    const observer = new ResizeObserver(() => map.invalidateSize())
    observer.observe(map.getContainer())
    return () => observer.disconnect()
  }, [map])

  return null
}

/** Reports the world position under the cursor. */
function CursorTracker({ onMove }: { onMove: (point: WorldPoint | null) => void }) {
  useMapEvents({
    mousemove: (event) => onMove(roundPoint(fromLatLng(event.latlng.lat, event.latlng.lng))),
    mouseout: () => onMove(null),
  })
  return null
}

export default function ClaimMap() {
  const [cursor, setCursor] = useState<WorldPoint | null>(null)
  const [claims, setClaims] = useState<Claim[]>([])
  const [world, setWorld] = useState<WorldId>('world')
  const [error, setError] = useState<string | null>(null)
  const [drawing, setDrawing] = useState(false)
  // 4.1 only reports what was drawn; 4.6 sends it to the API.
  const [drawn, setDrawn] = useState<WorldPoint[] | null>(null)
  const [placedCount, setPlacedCount] = useState(0)
  // Filled by the drawer while a session is live. Held in a ref because the
  // drawing session lives inside Leaflet, below this component.
  const undoRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    let cancelled = false

    fetchClaims()
      .then((loaded) => {
        if (!cancelled) setClaims(loaded)
      })
      .catch((cause: unknown) => {
        // Surfaced on the map rather than only in the console — a silently
        // empty map is indistinguishable from a server with no claims.
        if (!cancelled) setError(cause instanceof Error ? cause.message : String(cause))
      })

    return () => {
      cancelled = true
    }
  }, [])

  // Each dimension is its own coordinate space, so drawing them together would
  // stack unrelated claims on the same spot (SDLC §2).
  const visible = claims.filter((claim) => claim.world === world)

  return (
    <div className="map-shell">
      <MapContainer
        // CRS.Simple maps Leaflet's flat coordinate space straight onto the
        // world's X/Z, so no lat/lng conversion layer exists (SDLC §3).
        crs={CRS.Simple}
        center={[0, 0]}
        zoom={0}
        minZoom={-8}
        maxZoom={5}
        // Nothing to attribute — there is no tile provider behind this map.
        attributionControl={false}
        className="map"
      >
        <KeepSizeInSync />
        <ChunkGrid />
        <CursorTracker onMove={setCursor} />
        <ClaimDrawer
          active={drawing}
          undoRef={undoRef}
          onProgress={setPlacedCount}
          onComplete={(vertices) => {
            setDrawn(vertices)
            setDrawing(false)
          }}
        />

        {/*
          The shape just drawn, held in React state. Geoman's own layer is
          discarded on completion, so without this the outline would simply
          disappear at the moment you closed it. Dashed and amber to read as
          "not saved yet" — 4.6 sends it to the API.
        */}
        {drawn && (
          <Polygon
            positions={drawn.map(toLatLng)}
            pathOptions={{ color: '#d8a657', weight: 2, dashArray: '5 5', fillOpacity: 0.1 }}
          />
        )}

        {visible.map((claim) => (
          <Polygon
            key={claim.id}
            positions={claim.vertices.map(toLatLng)}
            pathOptions={{ color: '#7cc47c', weight: 2, fillOpacity: 0.15 }}
          >
            {/* sticky: follows the cursor, so it reads the claim you're over
                rather than the polygon's centre, which for a concave shape can
                sit outside the claim entirely. */}
            <Tooltip sticky>
              <span className="claim-title">{claim.title}</span>
              {/*
                Owner is optional. An unowned claim like Spawn drops the line
                entirely rather than showing "owned by none" — the same rule the
                in-game announcement follows (SDLC §2).

                Guarded on owner_name rather than owner_uuid: a claim can carry
                a UUID whose username hasn't been cached yet, and "owned by"
                followed by nothing is exactly the broken-looking output this
                step exists to prevent.
              */}
              {claim.owner_name && (
                <span className="claim-owner">owned by {claim.owner_name}</span>
              )}
            </Tooltip>
          </Polygon>
        ))}
      </MapContainer>

      <div className="draw-bar">
        <button
          type="button"
          className={drawing ? 'drawing' : undefined}
          onClick={() => {
            setDrawing((on) => !on)
            setDrawn(null)
          }}
        >
          {drawing ? 'Cancel' : 'Make claim'}
        </button>

        {drawing && (
          <>
            <button
              type="button"
              // Nothing to undo below two corners — Geoman ends the session
              // rather than removing the last one, so the button is disabled
              // instead of quietly cancelling the drawing.
              disabled={placedCount <= 1}
              onClick={() => undoRef.current?.()}
            >
              Undo
            </button>
            <span className="hint">
              {placedCount} placed — click the first point to close, right-click to undo
            </span>
          </>
        )}

        {drawn && (
          <>
            <span className="drawn">
              {drawn.length} points:{' '}
              {drawn.map((point) => `${point.x},${point.z}`).join('  ')}
            </span>
            <button type="button" onClick={() => setDrawn(null)}>
              Discard
            </button>
          </>
        )}
      </div>

      <div className="world-picker">
        {WORLDS.map((option) => (
          <button
            key={option.id}
            type="button"
            className={option.id === world ? 'active' : undefined}
            onClick={() => setWorld(option.id)}
          >
            {option.label}
            <span className="count">{claims.filter((c) => c.world === option.id).length}</span>
          </button>
        ))}
      </div>

      {/*
        The readout is how 3.2 gets verified: hover a known landmark and these
        numbers should match what /tp reports in-game.
      */}
      <div className="readout">
        {cursor ? (
          <>
            <span className="axis">X</span> {cursor.x}
            <span className="axis">Z</span> {cursor.z}
          </>
        ) : (
          <span className="idle">move the cursor over the map</span>
        )}
      </div>

      {error && <div className="error">could not load claims — {error}</div>}
    </div>
  )
}
