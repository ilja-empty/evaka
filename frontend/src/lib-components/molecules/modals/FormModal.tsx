// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { QueryKey } from '@tanstack/react-query'
import React, { FormEvent, useCallback } from 'react'

import { Failure, Result } from 'lib-common/api'
import { MutationDescription } from 'lib-common/query'

import AsyncButton from '../../atoms/buttons/AsyncButton'
import Button from '../../atoms/buttons/Button'
import MutateButton, { cancelMutation } from '../../atoms/buttons/MutateButton'
import { Gap } from '../../white-space'

import BaseModal, { ModalBaseProps, ModalButtons } from './BaseModal'

type FormModalProps = ModalBaseProps & {
  resolveAction: () => void
  resolveLabel: string
  resolveDisabled?: boolean
  rejectAction: () => void
  rejectLabel: string
}

export default React.memo(function FormModal({
  children,
  resolveAction,
  resolveLabel,
  resolveDisabled,
  rejectAction,
  rejectLabel,
  ...props
}: FormModalProps) {
  const onSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (!resolveDisabled) resolveAction()
    },
    [resolveAction, resolveDisabled]
  )

  return (
    <BaseModal {...props} close={rejectAction} closeLabel={rejectLabel}>
      <form onSubmit={onSubmit}>{children}</form>
      <ModalButtons $justifyContent="center">
        <Button
          primary
          type="submit"
          data-qa="modal-okBtn"
          onClick={resolveAction}
          disabled={resolveDisabled}
          text={resolveLabel}
        />
        <Gap horizontal size="s" />
        <Button
          onClick={rejectAction}
          data-qa="modal-cancelBtn"
          text={rejectLabel}
        />
      </ModalButtons>
    </BaseModal>
  )
})

type AsyncModalProps<T> = ModalBaseProps & {
  resolveAction: () => Promise<Result<T>> | void
  resolveLabel: string
  resolveDisabled?: boolean
  onSuccess: (value: T) => void
  onFailure?: (value: Failure<T>) => void
  rejectAction: () => void
  rejectLabel: string
}

function AsyncFormModal_<T>({
  children,
  resolveAction,
  resolveLabel,
  resolveDisabled,
  onSuccess,
  onFailure,
  rejectAction,
  rejectLabel,
  ...props
}: AsyncModalProps<T>) {
  return (
    <BaseModal {...props} close={rejectAction} closeLabel={rejectLabel}>
      {children}
      <ModalButtons $justifyContent="center">
        <AsyncButton
          primary
          text={resolveLabel}
          disabled={resolveDisabled}
          onClick={resolveAction}
          onSuccess={onSuccess}
          onFailure={onFailure}
          data-qa="modal-okBtn"
        />
        <Gap horizontal size="s" />
        <Button
          onClick={rejectAction}
          data-qa="modal-cancelBtn"
          text={rejectLabel}
        />
      </ModalButtons>
    </BaseModal>
  )
}

export const AsyncFormModal = React.memo(
  AsyncFormModal_
) as typeof AsyncFormModal_

type MutateFormModalProps<Arg, Data, Key extends QueryKey> = ModalBaseProps & {
  resolveMutation: MutationDescription<Arg, Data, Key>
  resolveAction: () => Arg | typeof cancelMutation
  resolveLabel: string
  resolveDisabled?: boolean
  onSuccess: (value: Data) => void
  onFailure?: (value: Failure<unknown>) => void
  rejectAction: () => void
  rejectLabel: string
}

function MutateFormModal_<Arg, Data, Key extends QueryKey>({
  children,
  resolveMutation,
  resolveAction,
  resolveLabel,
  resolveDisabled,
  onSuccess,
  onFailure,
  rejectAction,
  rejectLabel,
  ...props
}: MutateFormModalProps<Arg, Data, Key>) {
  return (
    <BaseModal {...props} close={rejectAction} closeLabel={rejectLabel}>
      {children}
      <ModalButtons $justifyContent="center">
        <MutateButton
          primary
          mutation={resolveMutation}
          text={resolveLabel}
          disabled={resolveDisabled}
          onClick={resolveAction}
          onSuccess={onSuccess}
          onFailure={onFailure}
          data-qa="modal-okBtn"
        />
        <Gap horizontal size="s" />
        <Button
          onClick={rejectAction}
          data-qa="modal-cancelBtn"
          text={rejectLabel}
        />
      </ModalButtons>
    </BaseModal>
  )
}

export const MutateFormModal = React.memo(
  MutateFormModal_
) as typeof MutateFormModal_
