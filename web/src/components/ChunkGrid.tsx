import { useEffect } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'

/** Below this many pixels apart, grid lines become visual noise. */
const MIN_LINE_SPACING_PX = 8

/** One chunk. The grid claims snap to (SDLC §2). */
const CHUNK = 16

const AXIS = 'rgba(220, 120, 120, 0.65)'
const MAJOR = 'rgba(255, 255, 255, 0.22)'
const MINOR = 'rgba(255, 255, 255, 0.08)'

/**
 * An infinite chunk grid, drawn onto canvas tiles.
 *
 * There is no tile server behind a CRS.Simple map, so without this the
 * background is a featureless void with no sense of scale or position.
 *
 * Spacing adapts to zoom: chunk lines while zoomed in, coarsening by factors of
 * four as you pull out, so the line count per tile stays roughly constant
 * instead of drawing thousands at low zoom.
 */
function createGridLayer(): L.GridLayer {
  const GridLayer = L.GridLayer.extend({
    createTile(this: L.GridLayer, coords: L.Coords): HTMLCanvasElement {
      const tile = document.createElement('canvas')
      const size = this.getTileSize()
      tile.width = size.x
      tile.height = size.y

      const ctx = tile.getContext('2d')
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const map = (this as any)._map as L.Map | undefined
      if (!ctx || !map) return tile

      // The world rectangle this tile covers, via Leaflet's own projection —
      // so the grid can never drift out of step with the map's transform.
      const topLeft = coords.scaleBy(size)
      const nw = map.unproject(topLeft, coords.z)
      const se = map.unproject(topLeft.add(size), coords.z)

      const west = nw.lng
      const east = se.lng
      // lat is negated Z, so the northern edge is the *smaller* Z.
      const zTop = -nw.lat
      const zBottom = -se.lat

      const pxPerBlockX = size.x / (east - west)
      const pxPerBlockZ = size.y / (zBottom - zTop)

      let spacing = CHUNK
      while (spacing * pxPerBlockX < MIN_LINE_SPACING_PX) spacing *= 4

      // Every fourth line gets emphasis, so there's a coarse reference to read
      // position against rather than an undifferentiated mesh.
      const majorEvery = spacing * 4

      ctx.lineWidth = 1

      const strokeFor = (value: number): string => {
        if (value === 0) return AXIS
        return value % majorEvery === 0 ? MAJOR : MINOR
      }

      for (let x = Math.ceil(west / spacing) * spacing; x <= east; x += spacing) {
        // +0.5 lands the stroke on a pixel centre; without it canvas straddles
        // two pixels and every line looks blurred.
        const px = Math.round((x - west) * pxPerBlockX) + 0.5
        ctx.strokeStyle = strokeFor(x)
        ctx.beginPath()
        ctx.moveTo(px, 0)
        ctx.lineTo(px, size.y)
        ctx.stroke()
      }

      for (let z = Math.ceil(zTop / spacing) * spacing; z <= zBottom; z += spacing) {
        const py = Math.round((z - zTop) * pxPerBlockZ) + 0.5
        ctx.strokeStyle = strokeFor(z)
        ctx.beginPath()
        ctx.moveTo(0, py)
        ctx.lineTo(size.x, py)
        ctx.stroke()
      }

      return tile
    },
  })

  // L.GridLayer.extend() is typed as producing a no-arg constructor, so the
  // options overload has to be reasserted here.
  const Constructable = GridLayer as unknown as new (
    options?: L.GridLayerOptions,
  ) => L.GridLayer

  return new Constructable({ tileSize: 256, noWrap: true })
}

export default function ChunkGrid() {
  const map = useMap()

  useEffect(() => {
    const layer = createGridLayer()
    layer.addTo(map)
    return () => {
      layer.remove()
    }
  }, [map])

  return null
}
