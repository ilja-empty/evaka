// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Failure, Paged, Result, Success } from 'lib-common/api'
import { GlobalRole } from 'lib-common/api-types/employee-auth'
import { MobileDevice } from 'lib-common/generated/api-types/pairing'
import {
  Employee,
  EmployeePreferredFirstName,
  EmployeeSetPreferredFirstNameUpdateRequest
} from 'lib-common/generated/api-types/pis'
import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import { JsonOf } from 'lib-common/json'
import { UUID } from 'lib-common/types'

import { FinanceDecisionHandlerOption } from '../state/invoicing-ui'
import { EmployeeUser } from '../types/employee'

import { client } from './client'

export async function getEmployees(): Promise<Result<Employee[]>> {
  return client
    .get<JsonOf<Employee[]>>(`/employee`)
    .then((res) =>
      res.data.map((data) => ({
        ...data,
        created: HelsinkiDateTime.parseIso(data.created),
        updated:
          data.updated !== null ? HelsinkiDateTime.parseIso(data.updated) : null
      }))
    )
    .then((v) => Success.of(v))
    .catch((e) => Failure.fromError(e))
}

export async function getFinanceDecisionHandlers(): Promise<
  Result<FinanceDecisionHandlerOption[]>
> {
  return client
    .get<JsonOf<Employee[]>>(`/employee/finance-decision-handler`)
    .then((res) =>
      res.data.map((data) => ({
        value: data.id,
        label: [data.firstName, data.lastName].join(' ')
      }))
    )
    .then((v) => Success.of(v))
    .catch((e) => Failure.fromError(e))
}

export async function updatePinCode(pinCode: string): Promise<Result<void>> {
  return client
    .post<void>(`/employee/pin-code`, { pin: pinCode })
    .then((res) => Success.of(res.data))
    .catch((e) => Failure.fromError(e))
}

export async function isPinCodeLocked(): Promise<Result<boolean>> {
  return client
    .get<boolean>(`/employee/pin-code/is-pin-locked`)
    .then((res) => Success.of(res.data))
    .catch((e) => Failure.fromError(e))
}

export function searchEmployees(
  page: number,
  pageSize: number,
  searchTerm?: string
): Promise<Result<Paged<EmployeeUser>>> {
  return client
    .post<JsonOf<Paged<EmployeeUser>>>('/employee/search', {
      page,
      pageSize,
      searchTerm
    })
    .then(({ data }) => Success.of(data))
    .catch((e) => Failure.fromError(e))
}

export function getEmployeeDetails(id: UUID): Promise<Result<EmployeeUser>> {
  return client
    .get<JsonOf<EmployeeUser>>(`/employee/${id}/details`)
    .then(({ data }) => Success.of(data))
    .catch((e) => Failure.fromError(e))
}

export function updateEmployee(
  id: UUID,
  globalRoles: GlobalRole[]
): Promise<Result<void>> {
  return client
    .put(`/employee/${id}`, {
      globalRoles
    })
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}

export function getPersonalMobileDevices(): Promise<Result<MobileDevice[]>> {
  return client
    .get<JsonOf<MobileDevice[]>>('/mobile-devices/personal')
    .then(({ data }) => Success.of(data))
    .catch((e) => Failure.fromError(e))
}

export function getEmployeePreferredFirstName(): Promise<
  Result<EmployeePreferredFirstName>
> {
  return client
    .get<JsonOf<EmployeePreferredFirstName>>('/employee/preferred-first-name')
    .then(({ data }) => Success.of(data))
    .catch((e) => Failure.fromError(e))
}

export function setEmployeePreferredFirstName(
  preferredFirstName: EmployeeSetPreferredFirstNameUpdateRequest
): Promise<Result<void>> {
  return client
    .post('/employee/preferred-first-name', preferredFirstName)
    .then(() => Success.of())
    .catch((e) => Failure.fromError(e))
}
