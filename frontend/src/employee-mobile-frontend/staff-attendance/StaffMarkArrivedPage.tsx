// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { addMinutes, differenceInMinutes, subMinutes } from 'date-fns'
import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState
} from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'

import { combine, Success } from 'lib-common/api'
import { formatTime } from 'lib-common/date'
import { StaffAttendanceType } from 'lib-common/generated/api-types/attendance'
import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import LocalDate from 'lib-common/local-date'
import LocalTime from 'lib-common/local-time'
import { useQueryResult } from 'lib-common/query'
import { UUID } from 'lib-common/types'
import useNonNullableParams from 'lib-common/useNonNullableParams'
import { mockNow } from 'lib-common/utils/helpers'
import Title from 'lib-components/atoms/Title'
import Button from 'lib-components/atoms/buttons/Button'
import MutateButton, {
  cancelMutation
} from 'lib-components/atoms/buttons/MutateButton'
import Select from 'lib-components/atoms/dropdowns/Select'
import TimeInput from 'lib-components/atoms/form/TimeInput'
import ErrorSegment from 'lib-components/atoms/state/ErrorSegment'
import { ContentArea } from 'lib-components/layout/Container'
import { FixedSpaceRow } from 'lib-components/layout/flex-helpers'
import { AlertBox, InfoBox } from 'lib-components/molecules/MessageBoxes'
import { EMPTY_PIN, PinInput } from 'lib-components/molecules/PinInput'
import { Gap } from 'lib-components/white-space'

import { renderResult } from '../async-rendering'
import TopBar from '../common/TopBar'
import { Actions, CustomTitle, TimeWrapper } from '../common/components'
import { useTranslation } from '../common/i18n'
import { UnitContext } from '../common/unit'
import { TallContentArea } from '../pairing/components'

import StaffAttendanceTypeSelection from './components/StaffAttendanceTypeSelection'
import { staffArrivalMutation, staffAttendanceQuery } from './queries'
import { getAttendanceArrivalDifferenceReasons } from './utils'

export default React.memo(function StaffMarkArrivedPage() {
  const { i18n } = useTranslation()
  const navigate = useNavigate()

  const { unitId, groupId, employeeId } = useNonNullableParams<{
    unitId: UUID
    groupId: UUID | 'all'
    employeeId: UUID
  }>()

  const { unitInfoResponse, reloadUnitInfo } = useContext(UnitContext)
  useEffect(() => {
    reloadUnitInfo()
  }, [reloadUnitInfo])

  const staffAttendanceResponse = useQueryResult(staffAttendanceQuery(unitId))

  const staffMember = useMemo(
    () =>
      staffAttendanceResponse.map((res) =>
        res.staff.find((s) => s.employeeId === employeeId)
      ),
    [employeeId, staffAttendanceResponse]
  )

  const [pinCode, setPinCode] = useState(EMPTY_PIN)
  const pinInputRef = useRef<HTMLInputElement>(null)
  const [time, setTime] = useState<string>(() =>
    HelsinkiDateTime.now().toLocalTime().format()
  )
  const [forceUpdateState, updateState] = useState<boolean>(true)
  const forceRerender = React.useCallback(
    () => updateState(!forceUpdateState),
    [forceUpdateState]
  )

  const [attendanceGroup, setAttendanceGroup] = useState<UUID | undefined>(
    groupId !== 'all' ? groupId : undefined
  )
  const [errorCode, setErrorCode] = useState<string | undefined>(undefined)
  const [attendanceType, setAttendanceType] = useState<StaffAttendanceType>()

  const getNow = () => mockNow() ?? new Date()

  const groupOptions = useMemo(
    () =>
      groupId === 'all'
        ? combine(staffMember, unitInfoResponse).map(
            ([staffMember, unitInfoResponse]) => {
              const groupIds = staffMember?.groupIds ?? []
              return unitInfoResponse.groups
                .filter((g) => groupIds.length === 0 || groupIds.includes(g.id))
                .map(({ id }) => id)
            }
          )
        : Success.of([]),
    [groupId, staffMember, unitInfoResponse]
  )

  const isValidTimeString = useMemo(
    () => LocalTime.tryParseWithPattern(time, 'HH:mm') !== undefined,
    [time]
  )

  useEffect(() => {
    if (
      attendanceGroup === undefined &&
      groupOptions.isSuccess &&
      groupOptions.value.length === 1
    ) {
      setAttendanceGroup(groupOptions.value[0])
    }
  }, [attendanceGroup, groupOptions])

  const firstPlannedStartOfTheDay = useMemo(
    () =>
      staffMember
        .map((staff) =>
          staff && staff.plannedAttendances.length > 0
            ? staff.plannedAttendances.reduce(
                (prev, curr) => (prev.start.isBefore(curr.start) ? prev : curr),
                staff.plannedAttendances[0]
              ).start
            : null
        )
        .getOrElse(null),
    [staffMember]
  )

  const selectedTimeDiffFromPlannedStartOfDayMinutes = useMemo(
    () =>
      firstPlannedStartOfTheDay && isValidTimeString
        ? differenceInMinutes(
            HelsinkiDateTime.now()
              .withTime(LocalTime.parse(time))
              .toSystemTzDate(),
            firstPlannedStartOfTheDay.toSystemTzDate()
          )
        : null,
    [firstPlannedStartOfTheDay, time, isValidTimeString]
  )

  const selectedTimeIsWithin30MinsFromNow = useCallback(
    (now: Date) =>
      isValidTimeString &&
      Math.abs(
        differenceInMinutes(
          HelsinkiDateTime.now()
            .withTime(LocalTime.parse(time))
            .toSystemTzDate(),
          now
        )
      ) <= 30,
    [time, isValidTimeString]
  )

  const hasPlan = useMemo(
    () => firstPlannedStartOfTheDay != null,
    [firstPlannedStartOfTheDay]
  )

  const backButtonText = useMemo(
    () =>
      staffMember
        .map((staffMember) =>
          staffMember
            ? `${staffMember.firstName} ${staffMember.lastName}`
            : i18n.common.back
        )
        .getOrElse(i18n.common.back),
    [i18n.common.back, staffMember]
  )

  const staffAttendanceDifferenceReasons: StaffAttendanceType[] = useMemo(
    () =>
      staffMember
        .map((staff) => {
          const parsedTime = LocalTime.tryParseWithPattern(time, 'HH:mm')
          if (!parsedTime || !staff?.spanningPlan) return []
          const arrived = HelsinkiDateTime.fromLocal(
            LocalDate.todayInHelsinkiTz(),
            parsedTime
          )
          return getAttendanceArrivalDifferenceReasons(
            staff.spanningPlan.start,
            arrived
          )
        })
        .getOrElse([]),
    [staffMember, time]
  )

  const showAttendanceTypeSelection = useMemo(() => {
    if (!hasPlan || !selectedTimeDiffFromPlannedStartOfDayMinutes) return false
    if (Math.abs(selectedTimeDiffFromPlannedStartOfDayMinutes) <= 5)
      return false
    return staffAttendanceDifferenceReasons.length >= 1
  }, [
    selectedTimeDiffFromPlannedStartOfDayMinutes,
    staffAttendanceDifferenceReasons,
    hasPlan
  ])

  return (
    <TallContentArea
      opaque={false}
      paddingHorizontal="zero"
      paddingVertical="zero"
    >
      <TopBar
        title={backButtonText}
        onBack={() => navigate(-1)}
        invertedColors
      />
      <ContentArea
        shadow
        opaque={true}
        paddingHorizontal="s"
        paddingVertical="m"
      >
        {renderResult(
          combine(unitInfoResponse, staffMember),
          ([unitInfo, staffMember]) => {
            if (staffMember === undefined)
              return (
                <ErrorSegment
                  title={i18n.attendances.staff.errors.employeeNotFound}
                />
              )

            const staffInfo = unitInfo.staff.find((s) => s.id === employeeId)
            const pinSet = staffInfo?.pinSet ?? true
            const pinLocked = staffInfo?.pinLocked || errorCode === 'PIN_LOCKED'

            const disableConfirmBecauseOfPlan =
              showAttendanceTypeSelection &&
              selectedTimeDiffFromPlannedStartOfDayMinutes != null &&
              selectedTimeDiffFromPlannedStartOfDayMinutes < -5 &&
              attendanceType == null

            const confirmDisabled =
              pinLocked ||
              !pinSet ||
              !isValidTimeString ||
              pinCode.join('').trim().length < 4 ||
              !selectedTimeIsWithin30MinsFromNow(getNow()) ||
              !attendanceGroup ||
              disableConfirmBecauseOfPlan

            const parsedTime = LocalTime.tryParse(time)

            const hasFutureCurrentDay =
              staffMember.latestCurrentDayAttendance &&
              (!staffMember.latestCurrentDayAttendance.departed ||
                (parsedTime &&
                  staffMember.latestCurrentDayAttendance.arrived.isAfter(
                    HelsinkiDateTime.now().withTime(parsedTime)
                  )))

            const hasConflictingFuture =
              staffMember.hasFutureAttendances || !!hasFutureCurrentDay

            return (
              <>
                <Title centered noMargin>
                  {i18n.attendances.staff.loginWithPin}
                </Title>
                {hasConflictingFuture && (
                  <AlertBox
                    message={i18n.attendances.staff.hasFutureAttendance}
                    wide
                  />
                )}
                <Gap />
                {!pinSet ? (
                  <ErrorSegment title={i18n.attendances.staff.pinNotSet} />
                ) : pinLocked ? (
                  <ErrorSegment title={i18n.attendances.staff.pinLocked} />
                ) : (
                  <PinInput
                    pin={pinCode}
                    onPinChange={setPinCode}
                    invalid={errorCode === 'WRONG_PIN'}
                  />
                )}
                <Gap />
                <TimeWrapper>
                  <CustomTitle>{i18n.attendances.arrivalTime}</CustomTitle>
                  <TimeInput
                    onChange={setTime}
                    value={time}
                    data-qa="input-arrived"
                    info={
                      !selectedTimeIsWithin30MinsFromNow(getNow())
                        ? {
                            status: 'warning',
                            text: i18n.common.validation.dateBetween(
                              formatTime(subMinutes(getNow(), 30)),
                              formatTime(addMinutes(getNow(), 30))
                            )
                          }
                        : undefined
                    }
                  />
                  {!selectedTimeIsWithin30MinsFromNow(getNow()) && (
                    <InfoBoxWrapper>
                      <InfoBox
                        message={i18n.attendances.timeDiffTooBigNotification}
                        data-qa="time-diff-too-big-notification"
                      />
                    </InfoBoxWrapper>
                  )}
                  {showAttendanceTypeSelection && (
                    <StaffAttendanceTypeSelection
                      i18n={i18n}
                      types={staffAttendanceDifferenceReasons}
                      selectedType={attendanceType}
                      setSelectedType={setAttendanceType}
                    />
                  )}
                  {selectedTimeIsWithin30MinsFromNow(getNow()) &&
                    renderResult(groupOptions, (groupOptions) =>
                      groupOptions.length > 1 ? (
                        <>
                          <Gap />
                          <CustomTitle>{i18n.common.group}</CustomTitle>
                          <Select
                            data-qa="group-select"
                            selectedItem={attendanceGroup}
                            items={groupOptions}
                            getItemLabel={(item) =>
                              unitInfo.groups.find((group) => group.id === item)
                                ?.name ?? ''
                            }
                            placeholder={i18n.attendances.chooseGroup}
                            onChange={(group) =>
                              setAttendanceGroup(group ?? undefined)
                            }
                          />
                        </>
                      ) : null
                    )}
                  <Gap />
                </TimeWrapper>
                <Gap size="xs" />
                <Actions>
                  <FixedSpaceRow fullWidth>
                    <Button
                      text={i18n.common.cancel}
                      onClick={() => navigate(-1)}
                    />
                    <MutateButton
                      primary
                      text={i18n.common.confirm}
                      disabled={confirmDisabled}
                      mutation={staffArrivalMutation}
                      onClick={() => {
                        if (selectedTimeIsWithin30MinsFromNow(getNow()))
                          if (attendanceGroup) {
                            return {
                              unitId,
                              request: {
                                employeeId,
                                groupId: attendanceGroup,
                                time: LocalTime.parse(time),
                                pinCode: pinCode.join(''),
                                type: attendanceType ?? null
                              }
                            }
                          } else {
                            return cancelMutation
                          }
                        else {
                          forceRerender()
                          return cancelMutation
                        }
                      }}
                      onSuccess={() => {
                        history.go(-1)
                      }}
                      onFailure={({ errorCode }) => {
                        setErrorCode(errorCode)
                        if (errorCode === 'WRONG_PIN') {
                          setPinCode(EMPTY_PIN)
                          pinInputRef.current?.focus()
                        }
                      }}
                      data-qa="mark-arrived-btn"
                    />
                  </FixedSpaceRow>
                </Actions>
              </>
            )
          }
        )}
      </ContentArea>
    </TallContentArea>
  )
})

const InfoBoxWrapper = styled.div`
  font-size: 16px;
`
