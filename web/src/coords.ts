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

/** The eight directions a claim edge may run: four axis-aligned, four diagonal. */
const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
  [1, 1],
  [1, -1],
  [-1, 1],
  [-1, -1],
]

/**
 * Snaps a point so the edge from [previous] runs at a multiple of 45°, with
 * both coordinates still on the chunk grid.
 *
 * **Deviates from SDLC §5, deliberately.** That formula snaps the *distance*
 * from the previous point to a multiple of 16 and the *angle* to 45°. Those two
 * rules cannot both hold on a diagonal: a 45° step of length 16 has components
 * of 16/√2 ≈ 11.31 — off-grid and fractional. §2 states the intent plainly,
 * that "diagonal steps of equal X/Z stay on-grid", and §7's chunk-index
 * optimisation depends on it; §5's formula simply fails to express that.
 *
 * So the *components* are snapped rather than the distance. A diagonal step is
 * 16 blocks on each axis — 16√2 ≈ 22.6 blocks of travel — instead of 16.
 * Visually identical, and every vertex stays on a chunk corner.
 *
 * Works by generating the nearest on-grid point along each of the eight legal
 * directions and keeping whichever lands closest to the cursor, rather than
 * snapping an angle and hoping the result is on-grid.
 */
export function snapFromPrevious(
  target: WorldPoint,
  previous: WorldPoint | null,
  grid: number = CHUNK_SIZE,
): WorldPoint {
  // The first corner has no edge leading to it, so there is no angle to honour.
  if (!previous) return snapToGrid(target, grid)

  const dx = target.x - previous.x
  const dz = target.z - previous.z

  let best: WorldPoint | null = null
  let bestDistance = Infinity

  for (const [ux, uz] of DIRECTIONS) {
    // How far along this direction the cursor sits, in grid steps. The divisor
    // is |u|² — 1 for axis moves, 2 for diagonals.
    const projection = (dx * ux + dz * uz) / (ux * ux + uz * uz)

    // At least one step: a zero-length edge would put two corners on the same
    // spot, which is a degenerate shape the server would reject anyway.
    const steps = Math.max(1, Math.round(projection / grid))

    const candidate: WorldPoint = {
      x: previous.x + ux * steps * grid,
      z: previous.z + uz * steps * grid,
    }

    const distance = (candidate.x - target.x) ** 2 + (candidate.z - target.z) ** 2
    if (distance < bestDistance) {
      bestDistance = distance
      best = candidate
    }
  }

  return best ?? snapToGrid(target, grid)
}
