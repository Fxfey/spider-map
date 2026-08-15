import { useEffect, useState } from 'react'
import { MapContainer, useMap, useMapEvents } from 'react-leaflet'
import { CRS } from 'leaflet'
import ChunkGrid from './ChunkGrid'
import { fromLatLng, roundPoint, type WorldPoint } from '../coords'

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
      </MapContainer>

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
    </div>
  )
}
