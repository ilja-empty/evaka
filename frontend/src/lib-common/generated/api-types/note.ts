// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

// GENERATED FILE: no manual modifications
/* eslint-disable prettier/prettier */

import LocalDate from '../../local-date'
import { UUID } from '../../types'

/**
* Generated from fi.espoo.evaka.note.child.daily.ChildDailyNote
*/
export interface ChildDailyNote {
  childId: UUID
  date: LocalDate
  feedingNote: ChildDailyNoteLevel | null
  id: UUID
  modifiedAt: Date
  modifiedBy: string
  note: string
  reminderNote: string
  reminders: ChildDailyNoteReminder[]
  sleepingMinutes: number | null
  sleepingNote: ChildDailyNoteLevel | null
}

/**
* Generated from fi.espoo.evaka.note.child.daily.ChildDailyNoteBody
*/
export interface ChildDailyNoteBody {
  feedingNote: ChildDailyNoteLevel | null
  note: string
  reminderNote: string
  reminders: ChildDailyNoteReminder[]
  sleepingMinutes: number | null
  sleepingNote: ChildDailyNoteLevel | null
}

/**
* Generated from fi.espoo.evaka.note.child.daily.ChildDailyNoteLevel
*/
export const childDailyNoteLevelValues = [
  'GOOD',
  'MEDIUM',
  'NONE'
] as const

export type ChildDailyNoteLevel = typeof childDailyNoteLevelValues[number]

/**
* Generated from fi.espoo.evaka.note.child.daily.ChildDailyNoteReminder
*/
export const childDailyNoteReminderValues = [
  'DIAPERS',
  'CLOTHES',
  'LAUNDRY'
] as const

export type ChildDailyNoteReminder = typeof childDailyNoteReminderValues[number]

/**
* Generated from fi.espoo.evaka.note.group.GroupNote
*/
export interface GroupNote {
  groupId: UUID
  id: UUID
  modifiedAt: Date
  modifiedBy: string
  note: string
}

/**
* Generated from fi.espoo.evaka.note.group.GroupNoteBody
*/
export interface GroupNoteBody {
  note: string
}

/**
* Generated from fi.espoo.evaka.note.NotesController.NotesByChildResponse
*/
export interface NotesByChildResponse {
  childDailyNote: ChildDailyNote | null
  groupNotes: GroupNote[]
}

/**
* Generated from fi.espoo.evaka.note.NotesController.NotesByGroupResponse
*/
export interface NotesByGroupResponse {
  childDailyNotes: ChildDailyNote[]
  groupNotes: GroupNote[]
}
