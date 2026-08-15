import type { LatLngExpression } from 'leaflet'

/** A point in Minecraft world coordinates — the numbers you'd type into /tp. */
export interface WorldPoint {
  x: number
  z: number
}

/**
 * Minecraft X/Z → Leaflet [lat, lng].
 *
 * Leaflet's lat axis increases *upward*. Minecraft's Z increases *south*, which
 * is downward on a north-up map — so Z is negated. X maps to lng unchanged,
 * since both increase to the east.
 *
 * Getting the sign wrong flips the map vertically, which looks entirely
 * plausible on a blank grid and only shows up when you compare it against a
 * real /tp coordinate — so it is worth being deliberate about.
 */
export function toLatLng({ x, z }: WorldPoint): LatLngExpression {
  return [-z, x]
}

/** Leaflet [lat, lng] → Minecraft X/Z. The exact inverse of {@link toLatLng}. */
export function fromLatLng(lat: number, lng: number): WorldPoint {
  return { x: lng, z: -lat }
}

/** Whole blocks, for display. Cursor positions land mid-block otherwise. */
export function roundPoint({ x, z }: WorldPoint): WorldPoint {
  return { x: Math.floor(x), z: Math.floor(z) }
}
