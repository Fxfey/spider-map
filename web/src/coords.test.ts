import { describe, expect, it } from 'vitest'
import {
  CHUNK_SIZE,
  fromLatLng,
  snapFromPrevious,
  snapToGrid,
  toLatLng,
  type WorldPoint,
} from './coords'

const p = (x: number, z: number): WorldPoint => ({ x, z })

describe('toLatLng / fromLatLng', () => {
  it('negates Z, because Leaflet counts latitude upward and Minecraft counts Z south', () => {
    expect(toLatLng(p(128, -64))).toEqual([64, 128])
  })

  it('round-trips', () => {
    const original = p(1234, -5678)
    const [lat, lng] = toLatLng(original) as [number, number]
    expect(fromLatLng(lat, lng)).toEqual(original)
  })

  it('puts south below and east right', () => {
    // The sign error that mirrors the map vertically and looks fine on a blank
    // grid: a larger Z must give a *smaller* latitude.
    const [northLat] = toLatLng(p(0, -100)) as [number, number]
    const [southLat] = toLatLng(p(0, 100)) as [number, number]
    expect(southLat).toBeLessThan(northLat)

    const [, westLng] = toLatLng(p(-100, 0)) as [number, number]
    const [, eastLng] = toLatLng(p(100, 0)) as [number, number]
    expect(eastLng).toBeGreaterThan(westLng)
  })
})

describe('snapToGrid', () => {
  it('rounds to the nearest chunk corner', () => {
    expect(snapToGrid(p(7, 7))).toEqual(p(0, 0))
    expect(snapToGrid(p(9, 9))).toEqual(p(16, 16))
    expect(snapToGrid(p(-9, -9))).toEqual(p(-16, -16))
  })

  it('leaves points that are already on the grid alone', () => {
    expect(snapToGrid(p(128, -64))).toEqual(p(128, -64))
  })
})

describe('snapFromPrevious', () => {
  it('grid-snaps the first corner, having no edge to take an angle from', () => {
    expect(snapFromPrevious(p(20, 30), null)).toEqual(p(16, 32))
  })

  // Hand-calculated cases: cursor position, and where the corner must land.
  const cases: Array<[string, WorldPoint, WorldPoint, WorldPoint]> = [
    ['due east',       p(0, 0), p(50, 3),    p(48, 0)],
    ['due west',       p(0, 0), p(-50, -3),  p(-48, 0)],
    ['due south',      p(0, 0), p(2, 50),    p(0, 48)],
    ['due north',      p(0, 0), p(-2, -50),  p(0, -48)],
    ['south-east 45°', p(0, 0), p(50, 50),   p(48, 48)],
    ['north-east 45°', p(0, 0), p(50, -50),  p(48, -48)],
    ['south-west 45°', p(0, 0), p(-50, 50),  p(-48, 48)],
    ['north-west 45°', p(0, 0), p(-50, -50), p(-48, -48)],
    ['offset origin',  p(128, -64), p(180, -60), p(176, -64)],
  ]

  it.each(cases)('%s', (_name, previous, cursor, expected) => {
    expect(snapFromPrevious(cursor, previous)).toEqual(expected)
  })

  it('never produces an off-grid corner, wherever the cursor is', () => {
    // 4.2's "done when": no off-grid vertex is ever produced.
    for (let x = -200; x <= 200; x += 7) {
      for (let z = -200; z <= 200; z += 11) {
        const corner = snapFromPrevious(p(x, z), p(0, 0))
        // Math.abs first: -16 % 16 is -0 in JavaScript, and Object.is — which
        // toBe uses — treats -0 and +0 as different values.
        expect(Math.abs(corner.x % CHUNK_SIZE)).toBe(0)
        expect(Math.abs(corner.z % CHUNK_SIZE)).toBe(0)
      }
    }
  })

  it('every edge runs at a multiple of 45°', () => {
    // 4.3's "done when". An edge is at 45° exactly when one component is zero
    // or the two have equal magnitude — no trigonometry needed, and no
    // floating-point tolerance to argue about.
    const previous = p(64, -32)

    for (let x = -300; x <= 300; x += 13) {
      for (let z = -300; z <= 300; z += 17) {
        const corner = snapFromPrevious(p(x, z), previous)
        const dx = Math.abs(corner.x - previous.x)
        const dz = Math.abs(corner.z - previous.z)

        expect(dx === 0 || dz === 0 || dx === dz).toBe(true)
      }
    }
  })

  it('never puts a corner on top of the previous one', () => {
    // A zero-length edge is a degenerate shape the server would reject.
    const previous = p(0, 0)
    for (let x = -8; x <= 8; x += 1) {
      for (let z = -8; z <= 8; z += 1) {
        expect(snapFromPrevious(p(x, z), previous)).not.toEqual(previous)
      }
    }
  })

  it('a diagonal step moves one chunk on each axis, not one chunk of travel', () => {
    // The documented deviation from SDLC §5. Snapping the distance to 16 would
    // give components of 16/√2 ≈ 11.31, which is off-grid and fractional.
    const corner = snapFromPrevious(p(12, 12), p(0, 0))
    expect(corner).toEqual(p(16, 16))
  })

  it('picks the nearest of the eight directions', () => {
    // Just south of due east: must stay on the east axis rather than jumping to
    // the diagonal.
    expect(snapFromPrevious(p(96, 20), p(0, 0))).toEqual(p(96, 0))
    // Clearly diagonal now.
    expect(snapFromPrevious(p(96, 80), p(0, 0))).toEqual(p(96, 96))
  })
})
