// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { isValid, parseJSON, set } from 'date-fns'
import { formatInTimeZone, utcToZonedTime, zonedTimeToUtc } from 'date-fns-tz'

import LocalDate from './local-date'
import LocalTime from './local-time'
import { isAutomatedTest, mockNow } from './utils/helpers'

const EUROPE_HELSINKI = 'Europe/Helsinki'

export default class HelsinkiDateTime {
  private constructor(readonly timestamp: number) {}

  get year(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getFullYear()
  }
  get month(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getMonth() + 1
  }
  get date(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getDate()
  }
  get hour(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getHours()
  }
  get minute(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getMinutes()
  }
  get second(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getSeconds()
  }
  get millisecond(): number {
    return utcToZonedTime(this.timestamp, EUROPE_HELSINKI).getMilliseconds()
  }
  private mapZoned(f: (timestamp: Date) => Date): HelsinkiDateTime {
    return HelsinkiDateTime.fromSystemTzDate(
      zonedTimeToUtc(
        f(utcToZonedTime(this.timestamp, EUROPE_HELSINKI)),
        EUROPE_HELSINKI
      )
    )
  }
  withDate(date: LocalDate): HelsinkiDateTime {
    return this.mapZoned((zoned) =>
      set(zoned, {
        year: date.year,
        month: date.month,
        date: date.date
      })
    )
  }
  withTime(time: LocalTime): HelsinkiDateTime {
    return this.mapZoned((zoned) =>
      set(zoned, {
        hours: time.hour,
        minutes: time.minute,
        seconds: time.second,
        milliseconds: nanosToMillis(time.nanosecond)
      })
    )
  }

  formatIso(): string {
    return formatInTimeZone(
      this.timestamp,
      EUROPE_HELSINKI,
      "yyyy-MM-dd'T'HH:mm:ss.SSSxxx"
    )
  }
  toString(): string {
    return this.formatIso()
  }
  toJSON(): string {
    return this.formatIso()
  }
  valueOf(): number {
    return this.timestamp
  }

  isBefore(other: HelsinkiDateTime): boolean {
    return this.timestamp < other.timestamp
  }
  isEqualOrBefore(other: HelsinkiDateTime): boolean {
    return this.timestamp === other.timestamp || this.isBefore(other)
  }
  isEqual(other: HelsinkiDateTime): boolean {
    return this.timestamp === other.timestamp
  }
  isEqualOrAfter(other: HelsinkiDateTime): boolean {
    return this.timestamp === other.timestamp || this.isAfter(other)
  }
  isAfter(other: HelsinkiDateTime): boolean {
    return this.timestamp > other.timestamp
  }

  toLocalDate(): LocalDate {
    return LocalDate.of(this.year, this.month, this.date)
  }
  toLocalTime(): LocalTime {
    return LocalTime.of(
      this.hour,
      this.minute,
      this.second,
      millisToNanos(this.millisecond)
    )
  }

  static fromLocal(date: LocalDate, time: LocalTime): HelsinkiDateTime {
    return HelsinkiDateTime.of(
      date.year,
      date.month,
      date.date,
      time.hour,
      time.minute,
      time.second,
      nanosToMillis(time.nanosecond)
    )
  }

  static of(
    year: number,
    month: number,
    date: number,
    hour = 0,
    minute = 0,
    second = 0,
    millisecond = 0
  ) {
    const result = HelsinkiDateTime.tryCreate(
      year,
      month,
      date,
      hour,
      minute,
      second,
      millisecond
    )
    if (!result) {
      throw new RangeError(
        `Invalid timestamp ${year}-${month}-${date}T${hour}:${minute}:${second}.${millisecond}`
      )
    }
    return result
  }
  static fromSystemTzDate(date: Date): HelsinkiDateTime {
    const result = HelsinkiDateTime.tryFromDate(date)
    if (!result) {
      throw new RangeError('Invalid date timestamp')
    }
    return result
  }
  static parseIso(value: string): HelsinkiDateTime {
    const result = HelsinkiDateTime.tryParseIso(value)
    if (!result) {
      throw new RangeError(`Invalid ISO date timestamp ${value}`)
    }
    return result
  }
  static tryParseIso(value: string): HelsinkiDateTime | undefined {
    return HelsinkiDateTime.tryFromDate(parseJSON(value))
  }
  static now(): HelsinkiDateTime {
    if (isAutomatedTest) {
      const now = mockNow()
      return HelsinkiDateTime.fromSystemTzDate(now ?? new Date())
    }
    return HelsinkiDateTime.fromSystemTzDate(new Date())
  }

  private static tryCreate(
    year: number,
    month: number,
    date: number,
    hour = 0,
    minute = 0,
    second = 0,
    millisecond = 0
  ): HelsinkiDateTime | undefined {
    if (!LocalDate.tryCreate(year, month, date)) return undefined
    if (!LocalTime.tryCreate(hour, minute, second, millisToNanos(millisecond)))
      return undefined
    return HelsinkiDateTime.tryFromDate(
      zonedTimeToUtc(
        new Date(year, month - 1, date, hour, minute, second, millisecond),
        EUROPE_HELSINKI
      )
    )
  }
  private static tryFromDate(date: Date): HelsinkiDateTime | undefined {
    return isValid(date) ? new HelsinkiDateTime(date.getTime()) : undefined
  }
}

function millisToNanos(millis: number): number {
  return millis * 1_000_000
}
function nanosToMillis(nanos: number): number {
  return Math.floor(nanos / 1_000_000)
}
