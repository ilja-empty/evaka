// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Failure, Result, Success } from 'lib-common/api'
import { AttachmentType } from 'lib-common/generated/api-types/attachment'
import { UUID } from 'lib-common/types'

import { API_URL, client } from './client'

async function doSaveAttachment(
  config: { path: string; params?: unknown },
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> {
  const formData = new FormData()
  formData.append('file', file)

  try {
    const { data } = await client.post<UUID>(config.path, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: config.params,
      onUploadProgress: ({ loaded, total }) =>
        onUploadProgress(
          total !== undefined && total !== 0
            ? Math.round((loaded * 100) / total)
            : 0
        )
    })
    return Success.of(data)
  } catch (e) {
    return Failure.fromError(e)
  }
}

export async function saveApplicationAttachment(
  applicationId: UUID,
  file: File,
  type: AttachmentType,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> {
  return await doSaveAttachment(
    { path: `/attachments/applications/${applicationId}`, params: { type } },
    file,
    onUploadProgress
  )
}

export async function saveIncomeStatementAttachment(
  incomeStatementId: UUID,
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> {
  return await doSaveAttachment(
    { path: `/attachments/income-statements/${incomeStatementId}` },
    file,
    onUploadProgress
  )
}

export async function saveIncomeAttachment(
  incomeId: UUID | null,
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> {
  return await doSaveAttachment(
    {
      path: incomeId ? `/attachments/income/${incomeId}` : `/attachments/income`
    },
    file,
    onUploadProgress
  )
}

export async function saveFeeAlterationAttachment(
  feeAlterationId: UUID | null,
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> {
  return await doSaveAttachment(
    {
      path: feeAlterationId
        ? `/attachments/fee-alteration/${feeAlterationId}`
        : `/attachments/fee-alteration`
    },
    file,
    onUploadProgress
  )
}

export const saveMessageAttachment = (
  draftId: UUID,
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> =>
  doSaveAttachment(
    { path: `/attachments/messages/${draftId}` },
    file,
    onUploadProgress
  )

export const savePedagogicalDocumentAttachment = (
  documentId: UUID,
  file: File,
  onUploadProgress: (percentage: number) => void
): Promise<Result<UUID>> =>
  doSaveAttachment(
    { path: `/attachments/pedagogical-documents/${documentId}` },
    file,
    onUploadProgress
  )

export const deleteAttachment = (id: UUID): Promise<Result<void>> =>
  client
    .delete(`/attachments/${id}`)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))

export function getAttachmentUrl(
  attachmentId: UUID,
  requestedFilename: string
): string {
  return `${API_URL}/attachments/${attachmentId}/download/${requestedFilename}`
}
