// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import { UUID } from 'lib-common/types'

import {
  insertDaycareGroupFixtures,
  insertDaycarePlacementFixtures,
  insertGuardianFixtures,
  resetDatabase
} from '../../dev-api'
import { initializeAreaAndPersonData } from '../../dev-api/data-init'
import {
  createDaycarePlacementFixture,
  daycareGroupFixture,
  Fixture,
  uuidv4
} from '../../dev-api/fixtures'
import { PersonDetail } from '../../dev-api/types'
import { CitizenChildPage } from '../../pages/citizen/citizen-children'
import CitizenHeader from '../../pages/citizen/citizen-header'
import { Page } from '../../utils/page'
import { enduserLogin } from '../../utils/user'

let page: Page
let child: PersonDetail
let templateIdHojks: UUID
let documentIdHojks: UUID
let templateIdPed: UUID
let documentIdPed: UUID
let header: CitizenHeader

const mockedNow = HelsinkiDateTime.of(2022, 7, 31, 13, 0)

beforeEach(async () => {
  await resetDatabase()

  const fixtures = await initializeAreaAndPersonData()
  await insertDaycareGroupFixtures([daycareGroupFixture])

  const unitId = fixtures.daycareFixture.id
  child = fixtures.enduserChildFixtureJari
  await insertGuardianFixtures([
    {
      guardianId: fixtures.enduserGuardianFixture.id,
      childId: child.id
    }
  ])

  await insertDaycarePlacementFixtures([
    createDaycarePlacementFixture(uuidv4(), child.id, unitId)
  ])

  templateIdHojks = (
    await Fixture.documentTemplate()
      .with({
        type: 'HOJKS',
        name: 'HOJKS 2023-2024'
      })
      .withPublished(true)
      .save()
  ).data.id
  documentIdHojks = (
    await Fixture.childDocument()
      .withTemplate(templateIdHojks)
      .withChild(child.id)
      .withPublishedAt(mockedNow)
      .withPublishedContent({
        answers: [
          {
            questionId: 'q1',
            type: 'TEXT',
            answer: 'test'
          }
        ]
      })
      .save()
  ).data.id

  templateIdPed = (
    await Fixture.documentTemplate()
      .with({
        type: 'PEDAGOGICAL_REPORT',
        name: 'Pedagoginen selvitys'
      })
      .withPublished(true)
      .save()
  ).data.id
  documentIdPed = (
    await Fixture.childDocument()
      .withTemplate(templateIdPed)
      .withChild(child.id)
      .withModifiedAt(mockedNow)
      .withPublishedAt(mockedNow)
      .withPublishedContent({
        answers: [
          {
            questionId: 'q1',
            type: 'TEXT',
            answer: 'test'
          }
        ]
      })
      .save()
  ).data.id

  page = await Page.open({ mockedTime: mockedNow.toSystemTzDate() })
  header = new CitizenHeader(page, 'desktop')
  await enduserLogin(page, fixtures.enduserGuardianFixture.ssn)
})

describe('Citizen child documents listing page', () => {
  test('Published hojks is in the list', async () => {
    await header.openChildPage(child.id)
    const childPage = new CitizenChildPage(page)
    await childPage.openCollapsible('vasu')
    await childPage.childDocumentLink(documentIdHojks).click()
    expect(
      page.url.endsWith(`/child-documents/${documentIdHojks}`)
    ).toBeTruthy()
    await page.find('h1').assertTextEquals('HOJKS 2023-2024')
  })

  test('Published pedagogical report is in the list', async () => {
    await header.openChildPage(child.id)
    const childPage = new CitizenChildPage(page)
    await childPage.openCollapsible('vasu')
    await childPage.childDocumentLink(documentIdPed).click()
    expect(page.url.endsWith(`/child-documents/${documentIdPed}`)).toBeTruthy()
    await page.find('h1').assertTextEquals('Pedagoginen selvitys')
  })
})
