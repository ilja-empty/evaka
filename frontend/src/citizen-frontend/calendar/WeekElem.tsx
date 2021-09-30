// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useEffect, useRef } from 'react'
import styled from 'styled-components'
import LocalDate from 'lib-common/local-date'
import colors from 'lib-customizations/common'
import { fontWeights } from 'lib-components/typography'
import { useTranslation } from '../localization'
import { defaultMargins } from 'lib-components/white-space'
import {
  FixedSpaceColumn,
  FixedSpaceRow
} from 'lib-components/layout/flex-helpers'
import { DailyReservationData } from './api'
import { Reservations } from './calendar-elements'
import { WeeklyData } from './CalendarListView'
import { headerHeightMobile } from 'citizen-frontend/header/const'

interface Props extends WeeklyData {
  selectDate: (date: LocalDate) => void
}

export default React.memo(function WeekElem({
  weekNumber,
  dailyReservations,
  selectDate
}: Props) {
  const i18n = useTranslation()
  return (
    <div>
      <WeekDiv>
        {i18n.common.datetime.weekShort} {weekNumber}
      </WeekDiv>
      <div>
        {dailyReservations.map((d) => (
          <DayElem
            dailyReservations={d}
            key={d.date.formatIso()}
            selectDate={selectDate}
          />
        ))}
      </div>
    </div>
  )
})

const WeekDiv = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${defaultMargins.s} 0 ${defaultMargins.xs};
  background-color: ${colors.brandEspoo.espooTurquoiseLight};
  color: ${colors.blues.dark};
  font-weight: ${fontWeights.semibold};
  font-size: 0.875rem;
  border-bottom: 1px solid ${colors.greyscale.lighter};
`

interface DayProps {
  dailyReservations: DailyReservationData
  selectDate: (date: LocalDate) => void
}

const DayElem = React.memo(function DayElem({
  dailyReservations,
  selectDate
}: DayProps) {
  const i18n = useTranslation()
  const ref = useRef<HTMLDivElement>()

  useEffect(() => {
    if (ref.current) {
      const pos = ref.current?.getBoundingClientRect().top

      if (pos) {
        const offset = headerHeightMobile + 16

        window.scrollTo({
          left: 0,
          top: pos - offset,
          behavior: 'smooth'
        })
      }
    }
  }, [])

  return (
    <DayDiv
      ref={(e) => {
        if (dailyReservations.date.isToday()) {
          ref.current = e ?? undefined
        }
      }}
      alignItems="center"
      today={dailyReservations.date.isToday()}
      onClick={() => selectDate(dailyReservations.date)}
      data-qa={`mobile-calendar-day-${dailyReservations.date.formatIso()}`}
    >
      <DayColumn spacing="xxs" holiday={dailyReservations.isHoliday}>
        <div>
          {
            i18n.common.datetime.weekdaysShort[
              dailyReservations.date.getIsoDayOfWeek() - 1
            ]
          }
        </div>
        <div>{dailyReservations.date.format('d.M.')}</div>
      </DayColumn>
      <div data-qa="reservations">
        <Reservations data={dailyReservations} />
      </div>
      {dailyReservations.date.isBefore(LocalDate.today()) && <HistoryOverlay />}
    </DayDiv>
  )
})

const DayDiv = styled(FixedSpaceRow)<{ today: boolean }>`
  position: relative;
  padding: ${defaultMargins.s} ${defaultMargins.s};
  border-bottom: 1px solid ${colors.greyscale.lighter};
  border-left: 6px solid
    ${(p) => (p.today ? colors.brandEspoo.espooTurquoise : 'transparent')};
  cursor: pointer;
`

const DayColumn = styled(FixedSpaceColumn)<{ holiday: boolean }>`
  width: 3rem;
  color: ${(p) => (p.holiday ? colors.greyscale.dark : colors.blues.dark)};
  font-weight: ${fontWeights.semibold};
`

const HistoryOverlay = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  opacity: 0.3;
  background-color: ${colors.blues.lighter};
`
