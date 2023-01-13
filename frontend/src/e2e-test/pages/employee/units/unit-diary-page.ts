// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import {
  AbsenceCategory,
  AbsenceType
} from 'lib-common/generated/api-types/daycare'
import LocalDate from 'lib-common/local-date'
import { UUID } from 'lib-common/types'

import { waitUntilEqual } from '../../../utils'
import {
  Checkbox,
  Element,
  Modal,
  Page,
  Radio,
  Select,
  TextInput
} from '../../../utils/page'

export class UnitDiaryPage {
  constructor(private page: Page) {}

  #unitName = this.page.find('[data-qa="attendances-unit-name"]')
  #groupSelector = new Select(
    this.page.find('[data-qa="attendances-group-select"]')
  )
  #absenceCell = (childId: UUID, date: LocalDate) =>
    new AbsenceCell(
      this.page.findByDataQa(`absence-cell-${childId}-${date.formatIso()}`)
    )

  #staffAttendanceCells = this.page.findAll('[data-qa="staff-attendance-cell"]')
  #addAbsencesButton = this.page.find('[data-qa="add-absences-button"]')

  async assertUnitName(expectedName: string) {
    await this.#unitName.assertTextEquals(expectedName)
  }

  async assertSelectedGroup(groupId: UUID) {
    await waitUntilEqual(() => this.#groupSelector.selectedOption, groupId)
  }

  async addAbsenceToChild(
    childId: UUID,
    date: LocalDate,
    type: AbsenceType | 'NO_ABSENCE',
    categories: AbsenceCategory[] = []
  ) {
    await this.#absenceCell(childId, date).select()
    await this.#addAbsencesButton.click()

    const modal = new AbsenceModal(this.page.find('[data-qa="absence-modal"]'))
    await modal.selectAbsenceType(type)
    for (const category of categories) {
      await modal.selectAbsenceCategory(category)
    }
    await modal.submit()
  }

  async childHasAbsence(
    childId: UUID,
    date: LocalDate,
    type: AbsenceType,
    category: AbsenceCategory
  ) {
    await this.#absenceCell(childId, date).assertAbsenceType(type, category)
  }

  async assertTooltipContains(
    childId: UUID,
    date: LocalDate,
    expectedTexts: string[]
  ) {
    const tooltipText = await this.#absenceCell(
      childId,
      date
    ).hoverAndGetTooltip()
    return expectedTexts.every((text) => tooltipText.includes(text))
  }

  async childHasNoAbsence(
    childId: UUID,
    date: LocalDate,
    category: AbsenceCategory
  ) {
    await this.#absenceCell(childId, date).assertNoAbsence(category)
  }

  async fillStaffAttendance(n: number, staffCount: number) {
    const cell = this.#staffAttendanceCells.nth(n)
    await new TextInput(cell.find('input')).fill(staffCount.toString())

    // Wait until saved
    await waitUntilEqual(() => cell.getAttribute('data-state'), 'clean')
  }

  async assertStaffAttendance(n: number, staffCount: number) {
    const input = new TextInput(this.#staffAttendanceCells.nth(n).find('input'))
    await input.assertValueEquals(staffCount.toString())
  }
}

export class AbsenceCell extends Element {
  constructor(private cell: Element) {
    super(cell)
  }

  async select() {
    await this.locator.click()
  }

  async assertAbsenceType(
    type: AbsenceType | 'empty',
    category: AbsenceCategory
  ) {
    await this.cell
      .find(
        `.absence-cell-${category === 'BILLABLE' ? 'right' : 'left'}-${type}`
      )
      .waitUntilVisible()
  }

  async assertNoAbsence(category: AbsenceCategory) {
    await this.assertAbsenceType('empty', category)
  }

  async hoverAndGetTooltip(): Promise<string> {
    await this.cell.hover()
    return (await this.cell.findByDataQa('absence-cell-tooltip').text) || ''
  }
}

export class AbsenceModal extends Modal {
  #absenceTypeRadio = (type: AbsenceType | 'NO_ABSENCE') =>
    new Radio(this.find(`[data-qa="absence-type-${type}"]`))
  #categoryCheckbox = (category: AbsenceCategory) =>
    new Checkbox(this.findByDataQa(`absences-select-${category}`))

  async selectAbsenceType(type: AbsenceType | 'NO_ABSENCE') {
    await this.#absenceTypeRadio(type).check()
  }

  async selectAbsenceCategory(category: AbsenceCategory) {
    await this.#categoryCheckbox(category).check()
  }
}
