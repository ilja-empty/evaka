// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { Fragment } from 'react'

import { AbsenceType } from 'lib-common/generated/api-types/daycare'
import { ChipWrapper, ChoiceChip } from 'lib-components/atoms/Chip'
import { Gap } from 'lib-components/white-space'

import { useTranslation } from '../../common/i18n'

export type AbsenceTypeWithNoAbsence = AbsenceType | 'NO_ABSENCE'

interface Props {
  absenceTypes: AbsenceTypeWithNoAbsence[]
  selectedAbsenceType: AbsenceTypeWithNoAbsence | undefined
  setSelectedAbsenceType: React.Dispatch<
    React.SetStateAction<AbsenceTypeWithNoAbsence | undefined>
  >
}

export default function AbsenceSelector({
  absenceTypes,
  selectedAbsenceType,
  setSelectedAbsenceType
}: Props) {
  const { i18n } = useTranslation()

  return (
    <Fragment>
      <ChipWrapper>
        {absenceTypes.map((absenceType) => (
          <Fragment key={absenceType}>
            <ChoiceChip
              text={i18n.absences.absenceTypes[absenceType]}
              selected={selectedAbsenceType === absenceType}
              onChange={() => setSelectedAbsenceType(absenceType)}
              data-qa={`mark-absent-${absenceType}`}
            />
            <Gap horizontal size="xxs" />
          </Fragment>
        ))}
      </ChipWrapper>
    </Fragment>
  )
}
