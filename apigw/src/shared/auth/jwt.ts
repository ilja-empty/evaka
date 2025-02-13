// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import jwt from 'jsonwebtoken'
import { readFileSync } from 'node:fs'
import { jwtKid, jwtPrivateKey } from '../config.js'

const privateKey = readFileSync(jwtPrivateKey)

export function createJwt(payload: {
  sub: string
  scope?: string
  evaka_employee_id?: string
  evaka_type:
    | 'citizen'
    | 'citizen_weak'
    | 'employee'
    | 'mobile'
    | 'system'
    | 'integration'
}): string {
  return jwt.sign(payload, privateKey, {
    algorithm: 'RS256',
    expiresIn: '48h',
    keyid: jwtKid
  })
}
