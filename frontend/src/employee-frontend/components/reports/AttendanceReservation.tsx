// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useState } from 'react'
import styled from 'styled-components'

import { getDaycareGroups, getDaycares } from 'employee-frontend/api/unit'
import { Loading } from 'lib-common/api'
import FiniteDateRange from 'lib-common/finite-date-range'
import { DaycareGroup } from 'lib-common/generated/api-types/daycare'
import { AttendanceReservationReportRow } from 'lib-common/generated/api-types/reports'
import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import LocalDate from 'lib-common/local-date'
import { UUID } from 'lib-common/types'
import { formatDecimal } from 'lib-common/utils/number'
import { useApiState } from 'lib-common/utils/useRestApi'
import Loader from 'lib-components/atoms/Loader'
import Title from 'lib-components/atoms/Title'
import ReturnButton from 'lib-components/atoms/buttons/ReturnButton'
import Combobox from 'lib-components/atoms/dropdowns/Combobox'
import MultiSelect from 'lib-components/atoms/form/MultiSelect'
import { Container, ContentArea } from 'lib-components/layout/Container'
import { Tbody, Td, Th, Thead, Tr } from 'lib-components/layout/Table'
import DateRangePicker from 'lib-components/molecules/date-picker/DateRangePicker'

import {
  AttendanceReservationReportFilters,
  getAssistanceReservationReport
} from '../../api/reports'
import ReportDownload from '../../components/reports/ReportDownload'
import { useTranslation } from '../../state/i18n'
import { FlexRow } from '../common/styled/containers'

import { FilterLabel, FilterRow, TableScrollable } from './common'

const dateFormat = 'EEEEEE d.M.'
const timeFormat = 'HH:mm'

interface AttendanceReservationReportUiRow {
  capacityFactor: string
  childCount: number
  childCountOver3: number
  childCountUnder3: number
  dateTime: HelsinkiDateTime
  groupId: UUID | null
  groupName: string | null
  staffCountRequired: string
}

export default React.memo(function AttendanceReservation() {
  const { lang, i18n } = useTranslation()

  const [unitId, setUnitId] = useState<UUID | null>(null)
  const [filters, setFilters] = useState<AttendanceReservationReportFilters>(
    () => {
      const defaultDate = LocalDate.todayInSystemTz().addWeeks(1)
      return {
        range: new FiniteDateRange(
          defaultDate.startOfWeek(),
          defaultDate.endOfWeek()
        ),
        groupIds: []
      }
    }
  )

  const [units] = useApiState(getDaycares, [])
  const [groups] = useApiState(
    () =>
      unitId !== null
        ? getDaycareGroups(unitId)
        : Promise.resolve(Loading.of<DaycareGroup[]>()),
    [unitId]
  )
  const [report] = useApiState(
    () =>
      unitId !== null
        ? getAssistanceReservationReport(unitId, filters)
        : Promise.resolve(Loading.of<AttendanceReservationReportRow[]>()),
    [unitId, filters]
  )

  const filteredRows = report
    .map<AttendanceReservationReportUiRow[]>((data) =>
      data.map((row) => ({
        ...row,
        capacityFactor: formatDecimal(row.capacityFactor),
        staffCountRequired: formatDecimal(row.staffCountRequired)
      }))
    )
    .getOrElse<AttendanceReservationReportUiRow[]>([])
  const filteredUnits = units
    .map((data) => data.sort((a, b) => a.name.localeCompare(b.name, lang)))
    .getOrElse([])
  const filteredGroups = groups
    .map((data) => data.sort((a, b) => a.name.localeCompare(b.name, lang)))
    .getOrElse([])

  const dates = filteredRows
    .map((row) => row.dateTime.toLocalDate().format(dateFormat, lang))
    .filter((value, index, array) => array.indexOf(value) === index)

  const rowsByGroupAndTime = filteredRows.reduce<
    Record<string, Map<string, AttendanceReservationReportUiRow[]>>
  >((data, row) => {
    const groupKey = row.groupId ?? 'ungrouped'
    const map = data[groupKey] ?? new Map()
    const time = row.dateTime.toLocalTime().format(timeFormat)
    const rows = map.get(time) ?? []
    rows.push(row)
    map.set(time, rows)
    data[groupKey] = map
    return data
  }, {})
  const entries = Object.entries(rowsByGroupAndTime)
  const showGroupTitle = entries.length > 1

  const tableComponents = entries.map(([groupId, rowsByTime]) => {
    const groupName = showGroupTitle
      ? filteredGroups.find((group) => group.id === groupId)?.name ??
        i18n.reports.attendanceReservation.ungrouped
      : undefined
    return (
      <TableScrollable
        key={groupId}
        data-qa="report-attendance-reservation-table"
      >
        <Thead sticky>
          <Tr>
            <Th>{groupName}</Th>
            {dates.map((date) => {
              return (
                <Th key={date} colSpan={5} align="center">
                  {date}
                </Th>
              )
            })}
          </Tr>
          <Tr>
            <Th stickyColumn>{i18n.reports.common.clock}</Th>
            {dates.map((date) => {
              return (
                <React.Fragment key={date}>
                  <Th>{i18n.reports.common.under3y}</Th>
                  <Th>{i18n.reports.common.over3y}</Th>
                  <Th>{i18n.reports.common.totalShort}</Th>
                  <Th>{i18n.reports.attendanceReservation.capacityFactor}</Th>
                  <Th>
                    {i18n.reports.attendanceReservation.staffCountRequired}
                  </Th>
                </React.Fragment>
              )
            })}
          </Tr>
        </Thead>
        <Tbody>{getTableBody(rowsByTime)}</Tbody>
      </TableScrollable>
    )
  })

  return (
    <Container>
      <ReturnButton label={i18n.common.goBack} />
      <ContentArea opaque>
        <Title size={1}>{i18n.reports.attendanceReservation.title}</Title>

        <FilterRow>
          <FilterLabel>{i18n.reports.common.period}</FilterLabel>
          <FlexRow>
            <DateRangePicker
              start={filters.range.start}
              end={filters.range.end}
              onChange={(start, end) => {
                if (start !== null && end !== null) {
                  setFilters({
                    ...filters,
                    range: new FiniteDateRange(start, end)
                  })
                }
              }}
              locale={lang}
              errorTexts={i18n.validationErrors}
              required={true}
            />
          </FlexRow>
        </FilterRow>
        <FilterRow>
          <FilterLabel>{i18n.reports.common.unitName}</FilterLabel>
          <FlexRow>
            <Combobox
              items={filteredUnits}
              onChange={(selectedItem) => {
                setUnitId(selectedItem !== null ? selectedItem.id : null)
                setFilters({ ...filters, groupIds: [] })
              }}
              selectedItem={
                filteredUnits.find((unit) => unit.id === unitId) ?? null
              }
              getItemLabel={(item) => item.name}
              placeholder={i18n.filters.unitPlaceholder}
            />
          </FlexRow>
        </FilterRow>
        <FilterRow>
          <FilterLabel>{i18n.reports.common.groupName}</FilterLabel>
          <div style={{ width: '100%' }}>
            <MultiSelect
              options={filteredGroups}
              onChange={(selectedItems) =>
                setFilters({
                  ...filters,
                  groupIds: selectedItems.map((selectedItem) => selectedItem.id)
                })
              }
              value={filteredGroups.filter((group) =>
                filters.groupIds.includes(group.id)
              )}
              getOptionId={(group) => group.id}
              getOptionLabel={(group) => group.name}
              placeholder=""
              isClearable={true}
            />
          </div>
        </FilterRow>

        {unitId !== null && report.isLoading && <Loader />}
        {report.isFailure && <span>{i18n.common.loadingFailed}</span>}
        {report.isSuccess && (
          <>
            <ReportDownload
              data={filteredRows.map((row) => ({
                ...row,
                dateTime: row.dateTime.format()
              }))}
              headers={[
                ...(filters.groupIds.length > 0
                  ? [
                      {
                        label: i18n.reports.common.groupName,
                        key: 'groupName' as const
                      }
                    ]
                  : []),
                {
                  label: i18n.reports.common.clock,
                  key: 'dateTime'
                },
                {
                  label: i18n.reports.common.under3y,
                  key: 'childCountUnder3'
                },
                {
                  label: i18n.reports.common.over3y,
                  key: 'childCountOver3'
                },
                {
                  label: i18n.reports.common.totalShort,
                  key: 'childCount'
                },
                {
                  label: i18n.reports.attendanceReservation.capacityFactor,
                  key: 'capacityFactor'
                },
                {
                  label: i18n.reports.attendanceReservation.staffCountRequired,
                  key: 'staffCountRequired'
                }
              ]}
              filename={`${i18n.reports.attendanceReservation.title} ${
                filteredUnits.find((unit) => unit.id === unitId)?.name ?? ''
              } ${filters.range.start.formatIso()}-${filters.range.end.formatIso()}.csv`}
            />
            {tableComponents}
          </>
        )}
      </ContentArea>
    </Container>
  )
})

const getTableBody = (
  rowsByTime: Map<string, AttendanceReservationReportUiRow[]>
) => {
  const components: React.ReactNode[] = []
  rowsByTime.forEach((rows, time) => {
    components.push(
      <Tr key={time}>
        <Td sticky>{time}</Td>
        {rows.map((row) => {
          const isToday = row.dateTime.toLocalDate().isToday()
          const isFuture = row.dateTime.isAfter(HelsinkiDateTime.now())
          const firstRow = time === '00:00'
          return (
            <React.Fragment key={row.dateTime.formatIso()}>
              <AttendanceReservationReportTd
                borderEdge={[
                  'left',
                  ...(isToday && firstRow ? (['top'] as const) : [])
                ]}
                isToday={isToday}
                isFuture={isFuture}
              >
                {row.childCountUnder3}
              </AttendanceReservationReportTd>
              <AttendanceReservationReportTd
                borderEdge={isToday && firstRow ? ['top'] : []}
                isToday={isToday}
                isFuture={isFuture}
              >
                {row.childCountOver3}
              </AttendanceReservationReportTd>
              <AttendanceReservationReportTd
                borderEdge={isToday && firstRow ? ['top'] : []}
                isToday={isToday}
                isFuture={isFuture}
              >
                {row.childCount}
              </AttendanceReservationReportTd>
              <AttendanceReservationReportTd
                borderEdge={isToday && firstRow ? ['top'] : []}
                isToday={isToday}
                isFuture={isFuture}
              >
                {row.capacityFactor}
              </AttendanceReservationReportTd>
              <AttendanceReservationReportTd
                borderEdge={[
                  'right',
                  ...(isToday && firstRow ? (['top'] as const) : [])
                ]}
                isToday={isToday}
                isFuture={isFuture}
              >
                {row.staffCountRequired}
              </AttendanceReservationReportTd>
            </React.Fragment>
          )
        })}
      </Tr>
    )
  })
  return components
}

export interface AttendanceReservationReportTdProps {
  borderEdge: Array<'top' | 'left' | 'right'>
  isToday: boolean
  isFuture: boolean
}

export const AttendanceReservationReportTd = styled(
  Td
)<AttendanceReservationReportTdProps>`
  text-align: center;
  ${(props) =>
    props.borderEdge.includes('top')
      ? `border-top: ${props.isToday ? '2px' : '1px'} solid ${
          props.isToday
            ? props.theme.colors.status.success
            : props.theme.colors.grayscale.g15
        };`
      : ''}
  ${(props) =>
    props.borderEdge.includes('right')
      ? `border-right: ${props.isToday ? '2px' : '1px'} solid ${
          props.isToday
            ? props.theme.colors.status.success
            : props.theme.colors.grayscale.g15
        };`
      : ''}
  ${(props) =>
    props.borderEdge.includes('left')
      ? `border-left: ${props.isToday ? '2px' : '1px'} solid ${
          props.isToday
            ? props.theme.colors.status.success
            : props.theme.colors.grayscale.g15
        };`
      : ''}
  ${(props) =>
    props.isFuture ? `color: ${props.theme.colors.grayscale.g70};` : ''}
`
