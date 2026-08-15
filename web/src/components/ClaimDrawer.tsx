import { useEffect, useRef } from 'react'
import { useMap } from 'react-leaflet'
import type { LatLng, LeafletMouseEvent, Layer, Marker, Polygon as LeafletPolygon } from 'leaflet'
import '@geoman-io/leaflet-geoman-free'
import '@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css'
import { fromLatLng, snapFromPrevious, toLatLng, type WorldPoint } from '../coords'

interface ClaimDrawerProps {
  active: boolean
  onComplete: (vertices: WorldPoint[]) => void
}

/**
 * The parts of Geoman's polygon draw handler we reach into.
 *
 * These are private API, which is why the dependency is pinned to an exact
 * version. Geoman has no grid or angle snapping of its own — its `snappable`
 * option snaps to other layers — so the only way to apply the rules from
 * SDLC §2 is to move its hint marker before it is read.
 */
interface GeomanPolygonDraw {
  _hintMarker?: Marker & { _snapped?: boolean }
  _syncHintLine?: () => void
  /** The in-progress outline. Its last point is the corner we snap against. */
  _layer?: { getLatLngs: () => LatLng[] }
}

/**
 * Click-to-place polygon drawing, on Geoman, snapped to the chunk grid.
 *
 * Geoman's own toolbar is never added — drawing is driven entirely by our own
 * button, so the map keeps one visual language.
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
      // Geoman's snapping is to *other layers*, which is not what SDLC §2 asks
      // for. The grid rule is applied by hand below.
      snappable: false,
      templineStyle: { color: '#7cc47c', weight: 2 },
      hintlineStyle: { color: '#7cc47c', weight: 2, dashArray: '4 4' },
      // finishOn is deliberately left at its default of null. Setting it to
      // 'click' binds "finish" to *every* map click, so the shape completes the
      // moment it has three points instead of when you close it. The default
      // finishes on clicking the first vertex, which is the behaviour 4.5 wants.
    })

    /**
     * Snaps the point that is about to be placed.
     *
     * Geoman reads the committed vertex from `_hintMarker.getLatLng()` rather
     * than from the click, and draws the rubber band to the same marker — so
     * moving it here snaps the preview and the placed vertex together. Setting
     * `_snapped` stops Geoman resetting the marker back to the raw cursor when
     * the click lands.
     *
     * Registered after `enableDraw`, so it runs after Geoman's own mousemove
     * handler and gets the final say on where the marker sits.
     */
    const snapHintMarker = (event: LeafletMouseEvent) => {
      const draw = map.pm.Draw.Polygon as unknown as GeomanPolygonDraw
      const hint = draw._hintMarker
      if (!hint) return

      // The corner already placed, which the new edge runs from. Absent for the
      // very first point, where there is no angle to honour.
      const placed = draw._layer?.getLatLngs() ?? []
      const last = placed.length > 0 ? placed[placed.length - 1] : undefined
      const previous = last ? fromLatLng(last.lat, last.lng) : null

      const cursor = fromLatLng(event.latlng.lat, event.latlng.lng)
      const snapped = snapFromPrevious(cursor, previous)
      hint.setLatLng(toLatLng(snapped))
      hint._snapped = true

      // The rubber band was already drawn to the unsnapped position by
      // Geoman's handler; redraw it now the marker has moved.
      draw._syncHintLine?.()
    }

    map.on('mousemove', snapHintMarker)

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
      map.off('mousemove', snapHintMarker)
      map.off('pm:create', handleCreate)
      map.pm.disableDraw()
    }
  }, [active, map])

  return null
}
