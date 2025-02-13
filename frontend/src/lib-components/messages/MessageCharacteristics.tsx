// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled, { useTheme } from 'styled-components'

import { MessageType } from 'lib-common/generated/api-types/messaging'
import { Theme } from 'lib-common/theme'
import { StaticChip } from 'lib-components/atoms/Chip'
import RoundIcon from 'lib-components/atoms/RoundIcon'
import { ScreenReaderOnly } from 'lib-components/atoms/ScreenReaderOnly'
import { FixedSpaceRow } from 'lib-components/layout/flex-helpers'

import { useTranslations } from '../i18n'

// TODO is the 20px line-height in StaticChip unintentional?
const Chip = styled(StaticChip)`
  line-height: 16px;
`

function chipColor(theme: Theme, type: 'MESSAGE' | 'BULLETIN'): string {
  switch (type) {
    case 'MESSAGE':
      return theme.colors.accents.a4violet
    case 'BULLETIN':
      return theme.colors.main.m1
  }
}

interface Props {
  type: MessageType
  urgent: boolean
}

export function MessageCharacteristics({ type, urgent }: Props) {
  const theme = useTheme()
  const i18n = useTranslations()
  return (
    <FixedSpaceRow spacing="xs" alignItems="center">
      <ScreenReaderOnly>{i18n.messages.thread.type}:</ScreenReaderOnly>
      {urgent && (
        <RoundIcon
          data-qa="urgent"
          aria-label={i18n.messages.thread.urgent}
          size="m"
          color={theme.colors.status.danger}
          content="!"
        />
      )}
      <Chip color={chipColor(theme, type)}>{i18n.messages.types[type]}</Chip>
    </FixedSpaceRow>
  )
}
