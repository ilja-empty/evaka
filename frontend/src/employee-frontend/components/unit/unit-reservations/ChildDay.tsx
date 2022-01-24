// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled, { css } from 'styled-components'
import {
  DailyServiceTimes,
  getTimesOnWeekday,
  isIrregular,
  isRegular,
  isVariableTime,
  TimeRange
} from 'lib-common/api-types/child/common'
import {
  ChildRecordOfDay,
  OperationalDay
} from 'lib-common/api-types/reservations'
import { Reservation } from 'lib-common/generated/api-types/reservations'
import { JsonOf } from 'lib-common/json'
import LocalDate from 'lib-common/local-date'
import { fontWeights } from 'lib-components/typography'
import { defaultMargins } from 'lib-components/white-space'
import { colors } from 'lib-customizations/common'
import { useTranslation } from '../../../state/i18n'
import AbsenceDay from './AbsenceDay'

function timeToMinutes(expected: string): number {
  const [hours, minutes] = expected.split(':').map(Number)
  return hours * 60 + minutes
}

/**
 * "Best effort" time difference calculation based on two "HH:mm" local time
 * strings without date part. This is only used for highlighting in the calendar
 * so edge cases are expected and might not matter much.
 *
 * The functionality can be improved once the reservation data model supports
 * reservations that span multiple days.
 */
function attendanceTimeDiffers(
  first: string | null | undefined,
  second: string | null | undefined,
  thresholdMinutes = 15
): boolean {
  if (!(first && second)) {
    return false
  }
  return timeToMinutes(first) - timeToMinutes(second) > thresholdMinutes
}

type ReservationOrServiceTime =
  | (TimeRange & { type: 'reservation' | 'service-time' })
  | undefined
const getReservationOrServiceTimeOfDay = (
  reservation: Reservation | null,
  serviceTimeOfDay: TimeRange | null
): ReservationOrServiceTime =>
  reservation
    ? {
        start: reservation.startTime,
        end: reservation.endTime,
        type: 'reservation'
      }
    : serviceTimeOfDay
    ? { ...serviceTimeOfDay, type: 'service-time' }
    : undefined

interface Props {
  day: OperationalDay
  dailyServiceTimes: DailyServiceTimes | null
  dataForAllDays: Record<JsonOf<LocalDate>, ChildRecordOfDay>
}

export default React.memo(function ChildDay({
  day,
  dailyServiceTimes,
  dataForAllDays
}: Props) {
  const { i18n } = useTranslation()

  const dailyData = dataForAllDays[day.date.formatIso()]

  if (!dailyData) return null

  if (day.isHoliday && !dailyData.reservation && !dailyData.attendance)
    return null

  const serviceTimesAvailable =
    dailyServiceTimes !== null && !isVariableTime(dailyServiceTimes)
  const serviceTimeOfDay =
    dailyServiceTimes === null || isVariableTime(dailyServiceTimes)
      ? null
      : isRegular(dailyServiceTimes)
      ? dailyServiceTimes.regularTimes
      : isIrregular(dailyServiceTimes)
      ? getTimesOnWeekday(dailyServiceTimes, day.date.getIsoDayOfWeek())
      : null

  const expectedTimeForThisDay = getReservationOrServiceTimeOfDay(
    dailyData.reservation,
    serviceTimeOfDay
  )
  const maybeServiceTimeIndicator =
    expectedTimeForThisDay?.type === 'service-time' &&
    ` ${i18n.unit.attendanceReservations.serviceTimeIndicator}`

  return (
    <DateCell>
      <TimesRow>
        {dailyData.absence ? (
          <AbsenceDay type={dailyData.absence.type} />
        ) : expectedTimeForThisDay ? (
          /* show actual reservation or service time if exists */
          <>
            <ReservationTime>
              {expectedTimeForThisDay.start}
              {maybeServiceTimeIndicator}
            </ReservationTime>
            <ReservationTime>
              {expectedTimeForThisDay.end}
              {maybeServiceTimeIndicator}
            </ReservationTime>
          </>
        ) : serviceTimesAvailable && serviceTimeOfDay === null ? (
          /* else if daily service times are known but there is none for this day of week, show day off */
          <ReservationTime>
            {i18n.unit.attendanceReservations.dayOff}
          </ReservationTime>
        ) : (
          /* else show missing service time */
          <ReservationTime warning>
            {i18n.unit.attendanceReservations.missingServiceTime}
          </ReservationTime>
        )}
      </TimesRow>
      <TimesRow>
        <AttendanceTime
          warning={attendanceTimeDiffers(
            expectedTimeForThisDay?.start,
            dailyData.attendance?.startTime
          )}
        >
          {dailyData.attendance?.startTime ?? '–'}
        </AttendanceTime>
        <AttendanceTime
          warning={attendanceTimeDiffers(
            dailyData.attendance?.endTime,
            expectedTimeForThisDay?.end
          )}
        >
          {dailyData.attendance?.endTime ?? '–'}
        </AttendanceTime>
      </TimesRow>
    </DateCell>
  )
})

const DateCell = styled.div`
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
`

const TimesRow = styled.div`
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  justify-content: space-evenly;
  padding: ${defaultMargins.xs};
  gap: ${defaultMargins.xs};

  :nth-child(even) {
    background: ${colors.grayscale.g4};
  }
`

const TimeCell = styled.div<{ warning?: boolean }>`
  flex: 1 0 54px;
  text-align: center;
  white-space: nowrap;
  ${(p) =>
    p.warning &&
    css`
      color: ${colors.accents.a2orangeDark};
    `};
`

const AttendanceTime = styled(TimeCell)`
  font-weight: ${fontWeights.semibold};
`

const ReservationTime = styled(TimeCell)``
