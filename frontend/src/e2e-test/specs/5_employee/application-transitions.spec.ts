// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import LocalDate from 'lib-common/local-date'
import LocalTime from 'lib-common/local-time'

import {
  cleanUpMessages,
  createDecisionPdf,
  execSimpleApplicationActions,
  getDecisionsByApplication,
  insertApplications,
  insertDecisionFixtures,
  insertDefaultServiceNeedOptions,
  rejectDecisionByCitizen,
  resetDatabase
} from '../../dev-api'
import {
  AreaAndPersonFixtures,
  initializeAreaAndPersonData
} from '../../dev-api/data-init'
import {
  applicationFixture,
  daycareFixture,
  decisionFixture,
  Fixture,
  preschoolFixture,
  uuidv4
} from '../../dev-api/fixtures'
import { Application, EmployeeDetail } from '../../dev-api/types'
import { ApplicationWorkbenchPage } from '../../pages/admin/application-workbench-page'
import ApplicationListView from '../../pages/employee/applications/application-list-view'
import ApplicationReadView from '../../pages/employee/applications/application-read-view'
import { UnitPage } from '../../pages/employee/units/unit'
import { Page } from '../../utils/page'
import { employeeLogin } from '../../utils/user'

const mockedTime = LocalDate.of(2021, 8, 16)
let page: Page
let applicationWorkbench: ApplicationWorkbenchPage
let applicationReadView: ApplicationReadView

let fixtures: AreaAndPersonFixtures
let serviceWorker: EmployeeDetail
let applicationId: string

beforeEach(async () => {
  await resetDatabase()
  await cleanUpMessages()
  fixtures = await initializeAreaAndPersonData()
  serviceWorker = (await Fixture.employeeServiceWorker().save()).data
  await insertDefaultServiceNeedOptions()
  await Fixture.feeThresholds().save()

  page = await Page.open({ mockedTime: mockedTime.toSystemTzDate() })
  applicationWorkbench = new ApplicationWorkbenchPage(page)
  applicationReadView = new ApplicationReadView(page)
})

describe('Application transitions', () => {
  test('Service worker accepts decision on behalf of the enduser', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      ),
      status: 'SENT' as const
    }
    applicationId = fixture.id

    await insertApplications([fixture])
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-decisions-without-proposal'
      ],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.acceptDecision('DAYCARE')

    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertApplicationStatus('Paikka vastaanotettu')
  })

  test('Service worker accepts decision on behalf of the enduser and forwards start date 2 weeks', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      ),
      status: 'SENT' as const
    }
    applicationId = fixture.id

    await insertApplications([fixture])
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-decisions-without-proposal'
      ],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.setDecisionStartDate(
      'DAYCARE',
      fixture.form.preferences.preferredStartDate?.addWeeks(2).format() ?? ''
    )

    await applicationReadView.acceptDecision('DAYCARE')
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertApplicationStatus('Paikka vastaanotettu')
  })

  test('Sending decision sets application to waiting confirmation state', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      ),
      status: 'SENT' as const
    }
    applicationId = fixture.id

    await insertApplications([fixture])
    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement', 'create-default-placement-plan'],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openDecisionQueue()
    await applicationWorkbench.sendDecisionsWithoutProposal(applicationId)

    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertApplicationStatus(
      'Vahvistettavana huoltajalla'
    )
  })

  test('Accepting decision for non vtj guardian sets application to waiting for mailing state', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.familyWithTwoGuardians.guardian
      ),
      status: 'SENT' as const
    }
    applicationId = fixture.id

    await insertApplications([fixture])
    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement', 'create-default-placement-plan'],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openDecisionQueue()
    await applicationWorkbench.sendDecisionsWithoutProposal(applicationId)

    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertApplicationStatus('Odottaa postitusta')
  })

  test('Placement dialog works', async () => {
    const preferredStartDate = mockedTime

    const group = await Fixture.daycareGroup()
      .with({ daycareId: fixtures.daycareFixture.id })
      .save()
    await Fixture.daycareCaretakers()
      .with({
        groupId: group.data.id,
        startDate: preferredStartDate,
        amount: 1
      })
      .save()

    const group2 = await Fixture.daycareGroup()
      .with({ daycareId: fixtures.preschoolFixture.id })
      .save()
    await Fixture.daycareCaretakers()
      .with({
        groupId: group2.data.id,
        startDate: preferredStartDate,
        amount: 2
      })
      .save()

    // Create existing placements to show meaningful occupancy values
    await Fixture.placement()
      .with({
        unitId: fixtures.daycareFixture.id,
        childId: fixtures.enduserChildFixturePorriHatterRestricted.id,
        startDate: preferredStartDate.formatIso()
      })
      .save()
    await Fixture.placement()
      .with({
        unitId: fixtures.preschoolFixture.id,
        childId: fixtures.enduserChildFixtureJari.id,
        startDate: preferredStartDate.formatIso()
      })
      .save()

    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.familyWithTwoGuardians.guardian,
        undefined,
        'DAYCARE',
        null,
        [daycareFixture.id],
        true,
        'SENT',
        preferredStartDate
      ),
      id: '6a9b1b1e-3fdf-11eb-b378-0242ac130002'
    }
    const applicationId = fixture.id

    await insertApplications([fixture])

    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement'],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openPlacementQueue()
    const placementDraftPage =
      await applicationWorkbench.openDaycarePlacementDialogById(applicationId)
    await placementDraftPage.waitUntilLoaded()

    await placementDraftPage.assertOccupancies(fixtures.daycareFixture.id, {
      max3Months: '14,3 %',
      max6Months: '14,3 %',
      max3MonthsSpeculated: '28,6 %',
      max6MonthsSpeculated: '28,6 %'
    })

    await placementDraftPage.addOtherUnit(fixtures.preschoolFixture.name)
    await placementDraftPage.assertOccupancies(fixtures.preschoolFixture.id, {
      max3Months: '7,1 %',
      max6Months: '7,1 %',
      max3MonthsSpeculated: '14,3 %',
      max6MonthsSpeculated: '14,3 %'
    })

    await placementDraftPage.placeToUnit(fixtures.preschoolFixture.id)
    await placementDraftPage.submit()

    await applicationWorkbench.waitUntilLoaded()
  })

  test('Placement dialog shows warning if guardian has restricted details', async () => {
    const restrictedDetailsGuardianApplication = {
      ...applicationFixture(
        fixtures.familyWithRestrictedDetailsGuardian.children[0],
        fixtures.familyWithRestrictedDetailsGuardian.guardian,
        fixtures.familyWithRestrictedDetailsGuardian.otherGuardian,
        'DAYCARE',
        'NOT_AGREED'
      ),
      id: '6a9b1b1e-3fdf-11eb-b378-0242ac130002'
    }
    const applicationId = restrictedDetailsGuardianApplication.id

    await insertApplications([restrictedDetailsGuardianApplication])

    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement'],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openPlacementQueue()

    const placementDraftPage =
      await applicationWorkbench.openDaycarePlacementDialogById(applicationId)
    await placementDraftPage.waitUntilLoaded()
    await placementDraftPage.assertRestrictedDetailsWarning()
  })

  test('Decision draft page works without unit selection', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.familyWithTwoGuardians.guardian,
        undefined,
        'PRESCHOOL',
        null,
        [preschoolFixture.id],
        true,
        'SENT',
        mockedTime
      ),
      id: '6a9b1b1e-3fdf-11eb-b378-0242ac130002'
    }
    const applicationId = fixture.id
    await insertApplications([fixture])

    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement'],
      HelsinkiDateTime.fromLocal(mockedTime, LocalTime.of(13, 40))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openPlacementQueue()
    const placementDraftPage =
      await applicationWorkbench.openDaycarePlacementDialogById(applicationId)
    await placementDraftPage.waitUntilLoaded()

    await placementDraftPage.placeToUnit(fixtures.preschoolFixture.id)
    await placementDraftPage.submit()
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openDecisionQueue()
    const decisionEditorPage =
      await applicationWorkbench.openDecisionEditorById(applicationId)
    await decisionEditorPage.waitUntilLoaded()

    await decisionEditorPage.save()
    await applicationWorkbench.waitUntilLoaded()

    await execSimpleApplicationActions(
      applicationId,
      ['send-decisions-without-proposal'],
      HelsinkiDateTime.fromLocal(mockedTime, LocalTime.of(13, 41))
    )

    const decisions = await getDecisionsByApplication(applicationId)
    expect(
      decisions
        .map(({ type, unit: { id: unitId } }) => ({ type, unitId }))
        .sort((a, b) => a.type.localeCompare(b.type))
    ).toEqual([
      { type: 'PRESCHOOL', unitId: preschoolFixture.id },
      { type: 'PRESCHOOL_DAYCARE', unitId: preschoolFixture.id }
    ])
  })

  test('Decision draft page works with unit selection', async () => {
    const fixture = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.familyWithTwoGuardians.guardian,
        undefined,
        'PRESCHOOL',
        null,
        [preschoolFixture.id],
        true,
        'SENT',
        mockedTime
      ),
      id: '6a9b1b1e-3fdf-11eb-b378-0242ac130002'
    }
    const applicationId = fixture.id
    await insertApplications([fixture])

    await execSimpleApplicationActions(
      applicationId,
      ['move-to-waiting-placement'],
      HelsinkiDateTime.fromLocal(mockedTime, LocalTime.of(13, 40))
    )

    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openPlacementQueue()
    const placementDraftPage =
      await applicationWorkbench.openDaycarePlacementDialogById(applicationId)
    await placementDraftPage.waitUntilLoaded()

    await placementDraftPage.placeToUnit(fixtures.preschoolFixture.id)
    await placementDraftPage.submit()
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openDecisionQueue()
    const decisionEditorPage =
      await applicationWorkbench.openDecisionEditorById(applicationId)
    await decisionEditorPage.waitUntilLoaded()

    await decisionEditorPage.selectUnit('PRESCHOOL_DAYCARE', daycareFixture.id)
    await decisionEditorPage.save()
    await applicationWorkbench.waitUntilLoaded()

    await execSimpleApplicationActions(
      applicationId,
      ['send-decisions-without-proposal'],
      HelsinkiDateTime.fromLocal(mockedTime, LocalTime.of(13, 41))
    )

    const decisions = await getDecisionsByApplication(applicationId)
    expect(
      decisions
        .map(({ type, unit: { id: unitId } }) => ({ type, unitId }))
        .sort((a, b) => a.type.localeCompare(b.type))
    ).toStrictEqual([
      { type: 'PRESCHOOL', unitId: daycareFixture.id },
      { type: 'PRESCHOOL_DAYCARE', unitId: daycareFixture.id }
    ])
  })

  test('Placement proposal flow', async () => {
    const fixture1 = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.familyWithTwoGuardians.guardian
      ),
      status: 'SENT' as const
    }
    applicationId = fixture1.id

    const applicationId2 = 'dd54782e-231c-4014-abaf-a63eed4e2627'
    const fixture2 = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.familyWithSeparatedGuardians.guardian
      ),
      status: 'SENT' as const,
      id: applicationId2
    }

    await insertApplications([fixture1, fixture2])
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-placement-proposal'
      ],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )
    await execSimpleApplicationActions(
      applicationId2,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-placement-proposal'
      ],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    const page2 = await Page.open()
    const unitPage = new UnitPage(page2)

    const unitSupervisor = (
      await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
    ).data
    await employeeLogin(page2, unitSupervisor)

    // unit supervisor
    await unitPage.navigateToUnit(fixtures.daycareFixture.id)
    let placementProposals = (await unitPage.openApplicationProcessTab())
      .placementProposals

    await placementProposals.assertAcceptButtonDisabled()
    await placementProposals.clickProposalAccept(applicationId)
    await placementProposals.assertAcceptButtonEnabled()
    await placementProposals.clickProposalAccept(applicationId2)

    await placementProposals.clickProposalReject(applicationId2)
    await placementProposals.selectProposalRejectionReason(0)
    await placementProposals.submitProposalRejectionReason()

    // service worker
    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()

    await applicationWorkbench.openPlacementProposalQueue()
    await applicationWorkbench.withdrawPlacementProposal(applicationId2)
    await applicationWorkbench.assertWithdrawPlacementProposalsButtonDisabled()

    // unit supervisor
    await unitPage.navigateToUnit(fixtures.daycareFixture.id)
    const applicationProcessPage = await unitPage.openApplicationProcessTab()
    placementProposals = applicationProcessPage.placementProposals
    await placementProposals.assertAcceptButtonEnabled()
    await placementProposals.clickAcceptButton()
    await placementProposals.assertPlacementProposalRowCount(0)
    await applicationProcessPage.waitUntilLoaded()

    await execSimpleApplicationActions(
      applicationId,
      ['confirm-decision-mailed'],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    await unitPage.navigateToUnit(fixtures.daycareFixture.id)
    const waitingConfirmation = (await unitPage.openApplicationProcessTab())
      .waitingConfirmation
    await waitingConfirmation.assertRowCount(1)
  })

  test('Placement proposal rejection status', async () => {
    const fixture1 = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.familyWithTwoGuardians.guardian
      ),
      status: 'SENT' as const
    }
    applicationId = fixture1.id

    await insertApplications([fixture1])

    const now = mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-placement-proposal'
      ],
      now
    )

    const page2 = await Page.open()
    const unitPage = new UnitPage(page2)

    await employeeLogin(
      page2,
      (
        await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
      ).data
    )

    // unit supervisor
    await unitPage.navigateToUnit(fixtures.daycareFixture.id)
    const placementProposals = (await unitPage.openApplicationProcessTab())
      .placementProposals

    await placementProposals.clickProposalReject(applicationId)
    await placementProposals.selectProposalRejectionReason(0)
    await placementProposals.submitProposalRejectionReason()
    await placementProposals.clickAcceptButton()

    // service worker
    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()
    await applicationWorkbench.openPlacementProposalQueue()

    await applicationWorkbench.assertApplicationStatusTextMatches(
      0,
      'TILARAJOITE'
    )
  })

  test('Decision cannot be accepted on behalf of guardian if application is in placement proposal state', async () => {
    const fixture1 = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.familyWithTwoGuardians.guardian
      ),
      status: 'SENT' as const
    }
    applicationId = fixture1.id

    await insertApplications([fixture1])
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-placement-proposal'
      ],
      mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    )

    const unitSupervisor = (
      await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
    ).data
    await employeeLogin(page, unitSupervisor)
    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertDecisionDisabled('DAYCARE')
  })

  test('Placement proposal rejection status', async () => {
    const fixture1 = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.familyWithTwoGuardians.guardian
      ),
      status: 'SENT' as const
    }
    applicationId = fixture1.id

    await insertApplications([fixture1])

    const now = mockedTime.toHelsinkiDateTime(LocalTime.of(12, 0))
    await execSimpleApplicationActions(
      applicationId,
      [
        'move-to-waiting-placement',
        'create-default-placement-plan',
        'send-placement-proposal'
      ],
      now
    )

    const page2 = await Page.open()
    const unitPage = new UnitPage(page2)

    await employeeLogin(
      page2,
      (
        await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
      ).data
    )

    // unit supervisor
    await unitPage.navigateToUnit(fixtures.daycareFixture.id)
    const placementProposals = (await unitPage.openApplicationProcessTab())
      .placementProposals

    await placementProposals.clickProposalReject(applicationId)
    await placementProposals.selectProposalRejectionReason(0)
    await placementProposals.submitProposalRejectionReason()
    await placementProposals.clickAcceptButton()

    // service worker
    await employeeLogin(page, serviceWorker)
    await page.goto(ApplicationListView.url)
    await applicationWorkbench.waitUntilLoaded()
    await applicationWorkbench.openPlacementProposalQueue()

    await applicationWorkbench.assertApplicationStatusTextMatches(
      0,
      'TILARAJOITE'
    )
  })

  test('Supervisor can download decision PDF only after it has been generated', async () => {
    const application = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      ),
      status: 'SENT' as const
    }
    applicationId = application.id

    await insertApplications([application])

    const decision = decisionFixture(
      applicationId,
      application.form.preferences.preferredStartDate?.formatIso() ?? '',
      application.form.preferences.preferredStartDate?.formatIso() ?? ''
    )
    const decisionId = decision.id

    // NOTE: This will NOT generate a PDF, just create the decision
    await insertDecisionFixtures([
      {
        ...decision,
        employeeId: serviceWorker.id
      }
    ])
    await employeeLogin(page, serviceWorker)

    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertDecisionDownloadPending(decision.type)

    // NOTE: No need to wait for pending async jobs as this is synchronous (unlike the normal flow of users creating
    // decisions that would trigger PDF generation as an async job).
    await createDecisionPdf(decisionId)

    await applicationReadView.navigateToApplication(applicationId)
    await applicationReadView.waitUntilLoaded()
    await applicationReadView.assertDecisionAvailableForDownload(decision.type)
  })

  test('Application rejected by citizen is shown for 2 weeks', async () => {
    const application1: Application = {
      ...applicationFixture(
        fixtures.enduserChildFixtureJari,
        fixtures.enduserGuardianFixture
      ),
      id: uuidv4(),
      status: 'WAITING_CONFIRMATION'
    }
    const application2: Application = {
      ...applicationFixture(
        fixtures.enduserChildFixtureKaarina,
        fixtures.enduserGuardianFixture
      ),
      id: uuidv4(),
      status: 'WAITING_CONFIRMATION'
    }
    const placementStartDate = '2021-08-16'

    await insertApplications([application1, application2])

    await Fixture.placementPlan()
      .with({
        applicationId: application1.id,
        unitId: fixtures.daycareFixture.id,
        periodStart: placementStartDate,
        periodEnd: placementStartDate
      })
      .save()

    await Fixture.placementPlan()
      .with({
        applicationId: application2.id,
        unitId: fixtures.daycareFixture.id,
        periodStart: placementStartDate,
        periodEnd: placementStartDate
      })
      .save()

    const decisionId = (
      await Fixture.decision()
        .with({
          applicationId: application2.id,
          employeeId: serviceWorker.id,
          unitId: fixtures.daycareFixture.id,
          startDate: placementStartDate,
          endDate: placementStartDate
        })
        .save()
    ).data.id

    await rejectDecisionByCitizen(decisionId)

    const unitSupervisor = (
      await Fixture.employeeUnitSupervisor(fixtures.daycareFixture.id).save()
    ).data

    async function assertApplicationRows(
      addDays: number,
      expectRejectedApplicationToBeVisible: boolean
    ) {
      const page = await Page.open({
        mockedTime:
          addDays !== 0
            ? LocalDate.todayInSystemTz().addDays(addDays).toSystemTzDate()
            : undefined
      })

      await employeeLogin(page, unitSupervisor)
      const unitPage = new UnitPage(page)
      await unitPage.navigateToUnit(fixtures.daycareFixture.id)
      const waitingConfirmation = (await unitPage.openApplicationProcessTab())
        .waitingConfirmation

      await waitingConfirmation.assertNotificationCounter(1)
      if (expectRejectedApplicationToBeVisible) {
        await waitingConfirmation.assertRowCount(2)
        await waitingConfirmation.assertRejectedRowCount(1)
        await waitingConfirmation.assertRow(application1.id, false)
        await waitingConfirmation.assertRow(application2.id, true)
      } else {
        await waitingConfirmation.assertRowCount(1)
        await waitingConfirmation.assertRejectedRowCount(0)
        await waitingConfirmation.assertRow(application1.id, false)
      }
      await page.close()
    }

    await assertApplicationRows(14, true)
    await assertApplicationRows(15, false)
  })
})
