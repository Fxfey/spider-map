import type { WorldPoint } from './coords'
export type { WorldPoint }

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

/**
 * Only what the client is allowed to choose. The id, version and timestamps
 * are the server's to set (SDLC §4), and `owner_uuid` waits for the username
 * lookup at 6.4 — a name without a UUID would be inconsistent data.
 */
export interface NewClaim {
  title: string
  world: WorldId
  vertices: WorldPoint[]
}

export async function createClaim(claim: NewClaim): Promise<Claim> {
  const response = await fetch('/api/claims', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(claim),
  })

  if (!response.ok) {
    // Every failure carries { "error": "..." } — a sentence written for
    // whoever drew the claim, so it can be shown as-is rather than
    // reworded here and drifting from what the server actually said.
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? `POST /api/claims returned ${response.status}`)
  }

  return response.json() as Promise<Claim>
}
