// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled from 'styled-components'
import { Result } from '@evaka/lib-common/src/api'
import { faArrowLeft } from '@evaka/lib-icons'
import { tabletMin } from '@evaka/lib-components/src/breakpoints'
import colors from '@evaka/lib-components/src/colors'
import { defaultMargins } from '@evaka/lib-components/src/white-space'
import { H1 } from '@evaka/lib-components/src/typography'
import { SpinnerSegment } from '@evaka/lib-components/src/atoms/state/Spinner'
import ErrorSegment from '@evaka/lib-components/src/atoms/state/ErrorSegment'
import IconButton from '@evaka/lib-components/src/atoms/buttons/IconButton'
import { useTranslation } from '../localization'
import { ReceivedBulletin } from '../messages/types'
import MessageListItem from '../messages/MessageListItem'

type Props = {
  bulletins: Result<ReceivedBulletin[]>
  activeBulletin: ReceivedBulletin | null
  onClickBulletin: (target: ReceivedBulletin) => void
  onReturn: () => void
}

export default React.memo(function MessagesList({
  bulletins,
  activeBulletin,
  onClickBulletin,
  onReturn
}: Props) {
  const t = useTranslation()

  return (
    <>
      {activeBulletin && (
        <MobileOnly>
          <Return
            icon={faArrowLeft}
            onClick={onReturn}
            altText={t.common.return}
          />
        </MobileOnly>
      )}
      <Container className={activeBulletin ? 'desktop-only' : undefined}>
        <HeaderContainer>
          <H1 noMargin>{t.messages.inboxTitle}</H1>
          {bulletins.isSuccess && bulletins.value.length === 0 && (
            <span>{t.messages.noMessages}</span>
          )}
        </HeaderContainer>

        {bulletins.isLoading && <SpinnerSegment />}
        {bulletins.isFailure && (
          <ErrorSegment title={t.common.errors.genericGetError} />
        )}
        {bulletins.isSuccess &&
          bulletins.value.map((bulletin) => (
            <MessageListItem
              key={bulletin.id}
              bulletin={bulletin}
              onClick={() => onClickBulletin(bulletin)}
              active={activeBulletin?.id === bulletin.id}
            />
          ))}
      </Container>
    </>
  )
})

const MobileOnly = styled.div`
  display: none;

  @media (max-width: ${tabletMin}) {
    display: block;
  }
`

const Return = styled(IconButton)`
  margin-left: ${defaultMargins.xs};
`

const Container = styled.div`
  min-width: 35%;
  max-width: 400px;
  min-height: 500px;
  background-color: ${colors.greyscale.white};

  @media (max-width: 750px) {
    min-width: 50%;
  }

  @media (max-width: ${tabletMin}) {
    width: 100%;
    max-width: 100%;
  }

  &.desktop-only {
    @media (max-width: ${tabletMin}) {
      display: none;
    }
  }
`

const HeaderContainer = styled.div`
  padding: ${defaultMargins.m};
`
