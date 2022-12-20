// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Child } from '../../dev-api/types'
import { waitUntilEqual } from '../../utils'
import { Page } from '../../utils/page'

export default class MobileChildPage {
  constructor(private readonly page: Page) {}

  #childName = this.page.find('[data-qa="child-name"]')
  #markPresentLink = this.page.find('[data-qa="mark-present-link"]')
  #markDepartedLink = this.page.find('[data-qa="mark-departed-link"]')
  #markAbsentLink = this.page.find('[data-qa="mark-absent-link"]')
  #cancelArrivalButton = this.page.find('[data-qa="cancel-arrival-button"]')
  #returnToPresentButton = this.page.find('[data-qa="return-to-present-btn"]')

  #modalOkButton = this.page.findByDataQa('modal-okBtn')

  #markAbsentBeforehandLink = this.page.find(
    '[data-qa="mark-absent-beforehand"]'
  )
  #sensitiveInfoLink = this.page.find('[data-qa="link-child-sensitive-info"]')

  #messageEditorLink = this.page.find('[data-qa="link-new-message"]')

  #notesLink = this.page.find('[data-qa="link-child-daycare-daily-note"]')

  #notesExistsBubble = this.page.find('[data-qa="daily-note-icon-bubble"]')

  #saveNoteButton = this.page.find('[data-qa="create-daily-note-btn"]')

  #goBack = this.page.find('[data-qa="back-btn"]')
  #goBackFromSensitivePage = this.page.find('[data-qa="go-back"]')

  #sensitiveInfo = {
    name: this.page.find('[data-qa="child-info-name"]'),
    allergies: this.page.find('[data-qa="child-info-allergies"]'),
    diet: this.page.find('[data-qa="child-info-diet"]'),
    medication: this.page.find('[data-qa="child-info-medication"]'),
    contactName: (n: number) =>
      this.page.find(`[data-qa="child-info-contact${n + 1}-name"]`),
    contactPhone: (n: number) =>
      this.page.find(`[data-qa="child-info-contact${n + 1}-phone"]`),
    contactEmail: (n: number) =>
      this.page.find(`[data-qa="child-info-contact${n + 1}-email"]`),
    backupPickupName: (n: number) =>
      this.page.find(`[data-qa="child-info-backup-pickup${n + 1}-name"]`),
    backupPickupPhone: (n: number) =>
      this.page.find(`[data-qa="child-info-backup-pickup${n + 1}-phone"]`)
  }

  #attendance = {
    arrivalTimes: this.page.findAllByDataQa('arrival-time'),
    departureTimes: this.page.findAllByDataQa('departure-time')
  }

  async waitUntilLoaded() {
    await this.#childName.waitUntilVisible()
  }

  async markFutureAbsences() {
    await this.#markAbsentBeforehandLink.click()
  }

  async selectMarkPresentView() {
    await this.#markPresentLink.click()
  }

  async selectMarkDepartedView() {
    await this.#markDepartedLink.click()
  }

  async selectMarkAbsentView() {
    await this.#markAbsentLink.click()
  }

  async returnToComing() {
    await this.#cancelArrivalButton.click()
    await this.#modalOkButton.click()
  }

  async returnToPresent() {
    await this.#returnToPresentButton.click()
  }
  async goBack() {
    await this.#goBack.click()
  }

  async goBackFromSensitivePage() {
    await this.#goBackFromSensitivePage.click()
  }

  async openSensitiveInfo() {
    await this.#sensitiveInfoLink.click()
  }

  async openMessageEditor() {
    await this.#messageEditorLink.click()
  }

  async assertSensitiveInfoIsShown(name: string) {
    await waitUntilEqual(() => this.#sensitiveInfo.name.text, name)
  }

  async assertSensitiveInfo(
    additionalInfo: Child,
    contacts: Array<{
      firstName: string
      lastName: string
      phone?: string
      email?: string | null
    }>,
    backupPickups: Array<{
      name: string
      phone: string
    }>
  ) {
    await waitUntilEqual(
      () => this.#sensitiveInfo.allergies.text,
      additionalInfo.allergies
    )
    await waitUntilEqual(
      () => this.#sensitiveInfo.diet.text,
      additionalInfo.diet
    )
    await waitUntilEqual(
      () => this.#sensitiveInfo.medication.text,
      additionalInfo.medication
    )

    for (let i = 0; i < contacts.length; i++) {
      const contact = contacts[i]
      await waitUntilEqual(
        () => this.#sensitiveInfo.contactName(i).text,
        `${contact.firstName} ${contact.lastName}`
      )
      if (contact.phone) {
        await waitUntilEqual(
          () => this.#sensitiveInfo.contactPhone(i).text,
          contact.phone
        )
      }
      if (contact.email) {
        await waitUntilEqual(
          () => this.#sensitiveInfo.contactEmail(i).text,
          contact.email
        )
      }
    }

    for (let i = 0; i < backupPickups.length; i++) {
      const backupPickup = backupPickups[i]
      await waitUntilEqual(
        () => this.#sensitiveInfo.backupPickupName(i).text,
        backupPickup.name
      )
      await waitUntilEqual(
        () => this.#sensitiveInfo.backupPickupPhone(i).text,
        backupPickup.phone
      )
    }
  }

  async openNotes() {
    await this.#notesLink.click()
    await this.#saveNoteButton.waitUntilVisible()
  }

  async assertNotesExist() {
    await this.#notesExistsBubble.waitUntilVisible()
  }

  async assertArrivalTimeInfoIsShown(arrivalTimeText: string) {
    await waitUntilEqual(
      () =>
        this.#attendance.arrivalTimes
          .allInnerTexts()
          .then((texts) =>
            texts.map((text) => text.replace(/\s/g, '')).join(',')
          ),
      arrivalTimeText
    )
  }

  async assertDepartureTimeInfoIsShown(departureTimeText: string) {
    await waitUntilEqual(
      () =>
        this.#attendance.departureTimes
          .allInnerTexts()
          .then((texts) =>
            texts.map((text) => text.replace(/\s/g, '')).join(',')
          ),
      departureTimeText
    )
  }
}
