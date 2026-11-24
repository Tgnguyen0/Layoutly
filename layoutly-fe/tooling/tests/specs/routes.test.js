import { describe, expect, it } from 'vitest'
import { readRoute } from '../../../src/lib/routes.js'

describe('public and legacy routing', () => {
  it.each([
    ['/', '', '', '/'],
    ['/', '', '#/login', '/login'],
    ['/register', '', '', '/register'],
    ['/', '', '#/forgot-password', '/forgot-password'],
    ['/', '', '#workspace', '/workspace'],
    ['/', '', '#converter', '/workspace'],
    ['/', '?snapshotId=bridge-snapshot', '', '/workspace'],
    ['/preview/snapshot-1', '', '', '/preview/snapshot-1'],
    ['/', '', '#grow', '/'],
    ['/', '', '#/does-not-exist', '/does-not-exist'],
  ])('routes %s %s %s to %s', (pathname, search, hash, expected) => {
    expect(readRoute({ pathname, search, hash })).toBe(expected)
  })
})
