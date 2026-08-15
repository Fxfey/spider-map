import type { WorldPoint } from './coords'

/**
 * A claim exactly as the API sends it.
 *
 * Field names stay snake_case to match the wire format (SDLC §4) rather than
 * being camelised on arrival — one less place for the two representations to
 * drift apart, and the shape here can be checked against the doc directly.
 */
export interface Claim {
  id: string
  title: string
  world: string
  owner_uuid: string | null
  owner_name: string | null
  created_by_uuid: string
  vertices: WorldPoint[]
  version: number
  created_at: string
  updated_at: string
}

/** The three standard dimensions. v1 supports no others (SDLC §2). */
export const WORLDS = [
  { id: 'world', label: 'Overworld' },
  { id: 'world_nether', label: 'Nether' },
  { id: 'world_the_end', label: 'The End' },
] as const

export type WorldId = (typeof WORLDS)[number]['id']

/** Public endpoint — viewing the map needs no login (SDLC §2). */
export async function fetchClaims(): Promise<Claim[]> {
  const response = await fetch('/api/claims')

  if (!response.ok) {
    throw new Error(`GET /api/claims returned ${response.status}`)
  }

  return response.json() as Promise<Claim[]>
}
