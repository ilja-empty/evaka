// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import type { Result } from 'lib-common/api'
import { Failure, Success } from 'lib-common/api'
import type {
  ChildDailyNoteBody,
  ChildStickyNoteBody,
  GroupNoteBody
} from 'lib-common/generated/api-types/note'
import type { UUID } from 'lib-common/types'

import { client } from './client'

export async function postGroupNote(
  groupId: UUID,
  body: GroupNoteBody
): Promise<Result<void>> {
  const url = `/daycare-groups/${groupId}/group-notes`
  return client
    .post(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function putGroupNote(
  id: UUID,
  body: GroupNoteBody
): Promise<Result<void>> {
  const url = `/group-notes/${id}`
  return client
    .put(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function deleteGroupNote(id: UUID): Promise<Result<void>> {
  const url = `/group-notes/${id}`
  return client
    .delete(url)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function postChildDailyNote(
  childId: UUID,
  body: ChildDailyNoteBody
): Promise<Result<void>> {
  const url = `/children/${childId}/child-daily-notes`
  return client
    .post(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function putChildDailyNote(
  id: UUID,
  body: ChildDailyNoteBody
): Promise<Result<void>> {
  const url = `/child-daily-notes/${id}`
  return client
    .put(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function deleteChildDailyNote(id: UUID): Promise<Result<void>> {
  const url = `/child-daily-notes/${id}`
  return client
    .delete(url)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function postChildStickyNote(
  childId: UUID,
  body: ChildStickyNoteBody
): Promise<Result<void>> {
  const url = `/children/${childId}/child-sticky-notes`
  return client
    .post(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function putChildStickyNote(
  id: UUID,
  body: ChildStickyNoteBody
): Promise<Result<void>> {
  const url = `/child-sticky-notes/${id}`
  return client
    .put(url, body)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export async function deleteChildStickyNote(id: UUID): Promise<Result<void>> {
  const url = `/child-sticky-notes/${id}`
  return client
    .delete(url)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}
