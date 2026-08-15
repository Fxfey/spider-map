import { useEffect, useRef } from 'react'
import { useMap } from 'react-leaflet'
import type { LatLng, Layer, Polygon as LeafletPolygon } from 'leaflet'
import '@geoman-io/leaflet-geoman-free'
import '@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css'
import { fromLatLng, type WorldPoint } from '../coords'

interface ClaimDrawerProps {
  active: boolean
  onComplete: (vertices: WorldPoint[]) => void
}

/**
 * Click-to-place polygon drawing, on Geoman.
 *
 * Geoman's own toolbar is never added — drawing is driven entirely by our own
 * button, so the map keeps one visual language.
 *
 * No snapping yet: 4.2 adds the chunk grid, 4.3 the 45° angle rule.
 */
export default function ClaimDrawer({ active, onComplete }: ClaimDrawerProps) {
  const map = useMap()

  /**
   * The callback is held in a ref, and deliberately kept out of the effect's
   * dependencies.
   *
   * A drawing session is long-lived state living inside Leaflet, but the parent
   * re-renders on every mousemove (the cursor readout). If the effect depended
   * on a prop whose identity changes each render, every mouse movement would
   * tear the session down and start a new one — Geoman's hint marker gets
   * recreated at the map centre each time, so it never follows the cursor and
   * the whole interaction flickers.
   */
  const onCompleteRef = useRef(onComplete)
  useEffect(() => {
    onCompleteRef.current = onComplete
  }, [onComplete])

  useEffect(() => {
    if (!active) return

    map.pm.enableDraw('Polygon', {
      // Geoman's snapping is to *other layers*; the grid and angle rules from
      // SDLC §2 are ours to apply, and arrive in 4.2 and 4.3.
      snappable: false,
      templineStyle: { color: '#7cc47c', weight: 2 },
      hintlineStyle: { color: '#7cc47c', weight: 2, dashArray: '4 4' },
      // finishOn is deliberately left at its default of null. Setting it to
      // 'click' binds "finish" to *every* map click, so the shape completes the
      // moment it has three points instead of when you close it. The default
      // finishes on clicking the first vertex, which is the behaviour 4.5 wants.
    })

    const handleCreate = (event: { layer: Layer }) => {
      const polygon = event.layer as LeafletPolygon
      const ring = (polygon.getLatLngs()[0] ?? []) as LatLng[]

      // Geoman leaves its own layer on the map. We re-render claims from React
      // state, so drop it rather than end up with two versions of one shape.
      polygon.remove()

      onCompleteRef.current(ring.map((point) => fromLatLng(point.lat, point.lng)))
    }

    map.on('pm:create', handleCreate)

    return () => {
      map.off('pm:create', handleCreate)
      map.pm.disableDraw()
    }
  }, [active, map])

  return null
}
