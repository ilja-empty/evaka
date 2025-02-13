// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import HelsinkiDateTime from 'lib-common/helsinki-date-time'

import config from '../../config'
import { resetDatabase } from '../../dev-api'
import {
  AreaAndPersonFixtures,
  initializeAreaAndPersonData
} from '../../dev-api/data-init'
import { Fixture } from '../../dev-api/fixtures'
import { EmployeeDetail, PersonDetail } from '../../dev-api/types'
import ChildInformationPage from '../../pages/employee/child-information'
import { ChildDocumentPage } from '../../pages/employee/documents/child-document'
import {
  DocumentTemplateEditorPage,
  DocumentTemplatesListPage
} from '../../pages/employee/documents/document-templates'
import EmployeeNav from '../../pages/employee/employee-nav'
import { waitUntilEqual } from '../../utils'
import { Page } from '../../utils/page'
import { employeeLogin } from '../../utils/user'

let fixtures: AreaAndPersonFixtures
let childFixture: PersonDetail
let admin: EmployeeDetail
let unitSupervisor: EmployeeDetail
let page: Page

const now = HelsinkiDateTime.of(2023, 2, 1, 12, 10, 0)

beforeEach(async () => {
  await resetDatabase()

  fixtures = await initializeAreaAndPersonData()
  childFixture = fixtures.enduserChildFixtureKaarina
  admin = (await Fixture.employeeAdmin().save()).data
  unitSupervisor = (
    await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
  ).data

  await Fixture.placement()
    .with({
      childId: childFixture.id,
      unitId: fixtures.daycareFixture.id,
      startDate: now.toLocalDate().subYears(1),
      endDate: now.toLocalDate().addYears(1)
    })
    .save()
})

describe('Employee - Child documents', () => {
  test('Full basic workflow for hojks', async () => {
    // Admin creates a template

    page = await Page.open({ mockedTime: now.toSystemTzDate() })
    await employeeLogin(page, admin)
    await page.goto(config.employeeUrl)
    const nav = new EmployeeNav(page)
    await nav.openAndClickDropdownMenuItem('document-templates')

    const documentTemplatesPage = new DocumentTemplatesListPage(page)
    await documentTemplatesPage.createNewButton.click()
    const documentName = 'HOJKS 2022-2023'
    await documentTemplatesPage.nameInput.fill(documentName)
    await documentTemplatesPage.typeSelect.selectOption('HOJKS')
    await documentTemplatesPage.validityStartInput.fill('01.08.2022')
    await documentTemplatesPage.confirmCreateButton.click()
    await documentTemplatesPage.openTemplate(documentName)

    const templateEditor = new DocumentTemplateEditorPage(page)
    await templateEditor.createNewSectionButton.click()
    const sectionName = 'Eka osio'
    await templateEditor.sectionNameInput.fill(sectionName)
    await templateEditor.confirmCreateSectionButton.click()

    const section = templateEditor.getSection(sectionName)
    await section.element.hover()
    await section.createNewQuestionButton.click()
    const questionName = 'Eka kysymys'
    await templateEditor.questionLabelInput.fill(questionName)
    await templateEditor.confirmCreateQuestionButton.click()

    await templateEditor.publishCheckbox.check()
    await templateEditor.saveButton.click()
    await templateEditor.saveButton.waitUntilHidden()
    await page.close()
    // End of admin creates a template

    // Unit supervisor creates a child document
    page = await Page.open({ mockedTime: now.toSystemTzDate() })
    await employeeLogin(page, unitSupervisor)
    await page.goto(
      `${config.employeeUrl}/child-information/${childFixture.id}`
    )
    let childInformationPage = new ChildInformationPage(page)
    let childDocumentsSection = await childInformationPage.openCollapsible(
      'childDocuments'
    )
    await childDocumentsSection.createDocumentButton.click()
    await childDocumentsSection.createModalTemplateSelect.assertTextEquals(
      'HOJKS 2022-2023'
    )
    await childDocumentsSection.modalOk.click()

    // Fill an answer and return
    let childDocument = new ChildDocumentPage(page)
    await childDocument.editButton.click()
    await childDocument.status.assertTextEquals('Luonnos')
    const answer = 'Jonkin sortin vastaus'
    let question = childDocument.getTextQuestion(sectionName, questionName)
    await question.fill(answer)
    await childDocument.savingIndicator.waitUntilHidden()
    await childDocument.previewButton.click()
    await childDocument.returnButton.click()

    // Assert status draft and unpublished, open the document again
    childDocumentsSection = await childInformationPage.openCollapsible(
      'childDocuments'
    )
    await waitUntilEqual(childDocumentsSection.childDocumentsCount, 1)
    let row = childDocumentsSection.childDocuments(0)
    await row.status.assertTextEquals('Luonnos')
    await row.published.assertTextEquals('-')
    await row.openLink.click()
    const documentUrl = page.url

    // Assert answer was saved
    await childDocument.editButton.click()
    question = childDocument.getTextQuestion(sectionName, questionName)
    await question.assertValueEquals(answer)

    // Publish without changing status
    await childDocument.previewButton.click()
    await childDocument.publish()

    // Assert status and publish time
    await childDocument.returnButton.click()
    childDocumentsSection = await childInformationPage.openCollapsible(
      'childDocuments'
    )
    await waitUntilEqual(childDocumentsSection.childDocumentsCount, 1)
    row = childDocumentsSection.childDocuments(0)
    await row.status.assertTextEquals('Luonnos')
    await row.published.assertTextEquals(now.format())
    await page.close()

    // go to next status twice
    const later = now.addHours(1)
    page = await Page.open({ mockedTime: later.toSystemTzDate() })
    await employeeLogin(page, unitSupervisor)
    await page.goto(documentUrl)
    childDocument = new ChildDocumentPage(page)
    await childDocument.goToNextStatus()
    await childDocument.status.assertTextEquals('Laadittu')
    await childDocument.goToNextStatus()
    await childDocument.status.assertTextEquals('Valmis')

    // Assert status and new publish time
    await childDocument.returnButton.click()
    childInformationPage = new ChildInformationPage(page)
    childDocumentsSection = await childInformationPage.openCollapsible(
      'childDocuments'
    )
    await waitUntilEqual(childDocumentsSection.childDocumentsCount, 1)
    row = childDocumentsSection.childDocuments(0)
    await row.status.assertTextEquals('Valmis')
    await row.published.assertTextEquals(later.format())
  })

  test('Pedagogical report only has two states', async () => {
    await Fixture.documentTemplate()
      .with({
        type: 'PEDAGOGICAL_REPORT',
        published: true
      })
      .save()

    // Unit supervisor creates a child document
    page = await Page.open({ mockedTime: now.toSystemTzDate() })
    await employeeLogin(page, unitSupervisor)
    await page.goto(
      `${config.employeeUrl}/child-information/${childFixture.id}`
    )
    const childInformationPage = new ChildInformationPage(page)
    const childDocumentsSection = await childInformationPage.openCollapsible(
      'childDocuments'
    )
    await childDocumentsSection.createDocumentButton.click()
    await childDocumentsSection.modalOk.click()

    // go to next status
    const childDocument = new ChildDocumentPage(page)
    await childDocument.status.assertTextEquals('Luonnos')
    await childDocument.goToNextStatus()
    await childDocument.status.assertTextEquals('Valmis')
  })
})
