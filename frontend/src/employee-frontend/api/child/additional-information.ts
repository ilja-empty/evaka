// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Failure, Result, Success } from 'lib-common/api'
import { AdditionalInformation } from 'lib-common/generated/api-types/daycare'
import { JsonOf } from 'lib-common/json'
import { UUID } from 'lib-common/types'

import { client } from '../client'

export async function getAdditionalInformation(
  id: UUID
): Promise<Result<AdditionalInformation>> {
  return client
    .get<JsonOf<AdditionalInformation>>(
      `/children/${id}/additional-information`
    )
    .then((res) => Success.of(res.data))
    .catch((e) => Failure.fromError(e))
}

export async function updateAdditionalInformation(
  id: UUID,
  data: AdditionalInformation
): Promise<Result<void>> {
  return client
    .put(`/children/${id}/additional-information`, data)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}
