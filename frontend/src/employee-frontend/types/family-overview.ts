// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { IncomeEffect } from 'lib-common/api-types/income'
import { FamilyContactRole } from 'lib-common/generated/api-types/pis'
import LocalDate from 'lib-common/local-date'

export interface FamilyOverviewPerson {
  personId: string
  firstName: string
  lastName: string
  dateOfBirth: LocalDate
  restrictedDetailsEnabled: boolean
  streetAddress: string
  postalCode: string
  postOffice: string
  headOfChild: string
  income?: FamilyOverviewIncome
}

export interface FamilyOverview {
  headOfFamily: FamilyOverviewPerson
  partner?: FamilyOverviewPerson
  children: FamilyOverviewPerson[]
  totalIncome?: FamilyOverviewIncome
}

export type FamilyOverviewPersonRole = 'HEAD' | 'PARTNER' | 'CHILD'

export interface FamilyOverviewRow {
  personId: string
  name: string
  role: FamilyOverviewPersonRole
  age: number
  restrictedDetailsEnabled: boolean
  address: string
  income?: FamilyOverviewIncome
}

interface FamilyOverviewIncome {
  effect?: IncomeEffect
  total?: number
}

export interface FamilyContact {
  id: string
  role: FamilyContactRole
  firstName: string | null
  lastName: string | null
  email: string | null
  phone: string | null
  backupPhone: string | null
  streetAddress: string
  postalCode: string
  postOffice: string
  priority: number
}
