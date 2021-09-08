// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Collapsible } from 'e2e-playwright/utils/element'
import { Page } from 'playwright'
import { waitUntilEqual } from 'e2e-playwright/utils'

export default class ChildInformationPage {
  constructor(private readonly page: Page) {}

  readonly feeAlterationsCollapsible = new Collapsible(
    this.page,
    '[data-qa="fee-alteration-collapsible"]'
  )
  readonly guardiansCollapsible = new Collapsible(
    this.page,
    '[data-qa="person-guardians-collapsible"]'
  )
  readonly placementsCollapsible = new Collapsible(
    this.page,
    '[data-qa="child-placements-collapsible"]'
  )
  readonly assistanceCollapsible = new Collapsible(
    this.page,
    '[data-qa="assistance-collapsible"]'
  )
  readonly backupCareCollapsible = new Collapsible(
    this.page,
    '[data-qa="backup-cares-collapsible"]'
  )
  readonly familyContactsCollapsible = new Collapsible(
    this.page,
    '[data-qa="family-contacts-collapsible"]'
  )
  readonly childApplicationsCollapsible = new Collapsible(
    this.page,
    '[data-qa="applications-collapsible"]'
  )
  readonly messageBlocklistCollapsible = new Collapsible(
    this.page,
    '[data-qa="child-message-blocklist-collapsible"]'
  )

  async childCollapsiblesVisible(params: {
    feeAlterations: boolean
    guardiansAndParents: boolean
    placements: boolean
    assistance: boolean
    backupCare: boolean
    familyContacts: boolean
    childApplications: boolean
    messageBlocklist: boolean
  }) {
    await waitUntilEqual(
      async () => ({
        feeAlterations: await this.feeAlterationsCollapsible.visible,
        guardiansAndParents: await this.guardiansCollapsible.visible,
        placements: await this.placementsCollapsible.visible,
        assistance: await this.assistanceCollapsible.visible,
        backupCare: await this.backupCareCollapsible.visible,
        familyContacts: await this.familyContactsCollapsible.visible,
        childApplications: await this.childApplicationsCollapsible.visible,
        messageBlocklist: await this.messageBlocklistCollapsible.visible
      }),
      params
    )
  }

  async addParentToBlockList(parentId: string) {
    await this.messageBlocklistCollapsible.open()
    await this.messageBlocklistCollapsible
      .find(`[data-qa="recipient-${parentId}"] [data-qa="blocklist-checkbox"]`)
      .click()
  }
}
