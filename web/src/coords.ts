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

/** One chunk. Claim corners snap to this grid (SDLC §2). */
export const CHUNK_SIZE = 16

/**
 * Snaps a point to the nearest chunk corner.
 *
 * Chunk alignment is not cosmetic: it is what lets the plugin find a player's
 * claim by looking up their current chunk instead of testing every claim on
 * every move (SDLC §7). A vertex that is off-grid quietly breaks that
 * assumption, so this is applied to the drawing preview rather than only on
 * save — you always place the point that will actually be stored.
 */
export function snapToGrid({ x, z }: WorldPoint, grid: number = CHUNK_SIZE): WorldPoint {
  return {
    x: Math.round(x / grid) * grid,
    z: Math.round(z / grid) * grid,
  }
}
