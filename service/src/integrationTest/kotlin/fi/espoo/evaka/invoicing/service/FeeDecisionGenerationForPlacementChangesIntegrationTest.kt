// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.service

import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.insertGeneralTestFixtures
import fi.espoo.evaka.invoicing.controller.FeeDecisionController
import fi.espoo.evaka.invoicing.data.feeDecisionQuery
import fi.espoo.evaka.invoicing.domain.FeeDecision
import fi.espoo.evaka.invoicing.domain.FeeDecisionStatus
import fi.espoo.evaka.placement.updatePlacementStartAndEndDate
import fi.espoo.evaka.shared.PlacementId
import fi.espoo.evaka.shared.async.AsyncJob
import fi.espoo.evaka.shared.async.AsyncJobRunner
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.dev.insertTestParentship
import fi.espoo.evaka.shared.dev.insertTestPlacement
import fi.espoo.evaka.shared.domain.DateRange
import fi.espoo.evaka.shared.domain.MockEvakaClock
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDecisionMaker_2
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FeeDecisionGenerationForPlacementChangesIntegrationTest :
    FullApplicationTest(resetDbBeforeEach = true) {
    @Autowired private lateinit var generator: FinanceDecisionGenerator
    @Autowired private lateinit var feeDecisionController: FeeDecisionController
    @Autowired private lateinit var asyncJobRunner: AsyncJobRunner<AsyncJob>

    val originalRange = dateRange(10, 20)

    val now = MockEvakaClock(2023, 1, 1, 0, 0)
    val admin = AuthenticatedUser.Employee(testDecisionMaker_2.id, setOf(UserRole.ADMIN))
    val placementId = PlacementId(UUID.randomUUID())
    lateinit var sentDecision: FeeDecision

    @BeforeEach
    fun beforeEach() {
        db.transaction { tx ->
            tx.insertGeneralTestFixtures()
            tx.insertTestPlacement(
                id = placementId,
                childId = testChild_1.id,
                unitId = testDaycare.id,
                startDate = originalRange.start,
                endDate = originalRange.end!!
            )
            tx.insertTestParentship(
                testAdult_1.id,
                testChild_1.id,
                startDate = originalRange.start.minusYears(1),
                endDate = originalRange.end!!.plusYears(1)
            )
        }
        generate()
        sendAllFeeDecisions()
        val decisions = getAllFeeDecisions()
        assertEquals(1, decisions.size)
        assertEquals(FeeDecisionStatus.SENT, decisions.first().status)
        sentDecision = decisions.first()
    }

    @Test
    fun `no changes`() {
        generate()

        val feeDecisions = getAllFeeDecisions()
        assertEquals(listOf(sentDecision), feeDecisions)
    }

    @Test
    fun `start date moves earlier`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(5), day(20)) }
        generate()

        assertDrafts(listOf(dateRange(5, 9) to 1))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(5, 9), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(10, 20), 1),
            )
        )
    }

    @Test
    fun `start date moves later`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(15), day(20)) }
        generate()

        assertDrafts(
            listOf(
                dateRange(10, 14) to 0,
                dateRange(15, 20) to 1,
            )
        )

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.ANNULLED, dateRange(10, 20), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(15, 20), 1),
            )
        )
    }

    @Test
    fun `end date moves earlier`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(10), day(15)) }
        generate()

        assertDrafts(listOf(dateRange(16, 20) to 0))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(10, 15), 1),
            )
        )
    }

    @Test
    fun `end date moves later`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(10), day(25)) }
        generate()

        assertDrafts(listOf(dateRange(21, 25) to 1))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(10, 20), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(21, 25), 1),
            )
        )
    }

    @Test
    fun `start date moves earlier and end date moves earlier`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(5), day(15)) }
        generate()

        assertDrafts(listOf(dateRange(5, 9) to 1, dateRange(16, 20) to 0))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(5, 9), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(10, 15), 1),
            )
        )
    }

    @Test
    fun `start date moves earlier and end date moves later`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(5), day(25)) }
        generate()

        assertDrafts(listOf(dateRange(5, 9) to 1, dateRange(21, 25) to 1))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(5, 9), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(10, 20), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(21, 25), 1),
            )
        )
    }

    @Test
    fun `start date moves later and end date moves earlier`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(15), day(15)) }
        generate()

        assertDrafts(listOf(dateRange(10, 14) to 0, dateRange(15, 15) to 1, dateRange(16, 20) to 0))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.ANNULLED, dateRange(10, 20), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(15, 15), 1)
            )
        )
    }

    @Test
    fun `start date moves later and end date moves later`() {
        db.transaction { tx -> tx.updatePlacementStartAndEndDate(placementId, day(15), day(25)) }
        generate()

        assertDrafts(listOf(dateRange(10, 14) to 0, dateRange(15, 25) to 1))

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.ANNULLED, dateRange(10, 20), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(15, 25), 1)
            )
        )
    }

    @Test
    fun `break in placement on days 14-15`() {
        db.transaction { tx ->
            tx.updatePlacementStartAndEndDate(placementId, day(10), day(13))
            tx.insertTestPlacement(
                id = PlacementId(UUID.randomUUID()),
                childId = testChild_1.id,
                unitId = testDaycare.id,
                startDate = day(16),
                endDate = originalRange.end!!
            )
        }

        generate()

        assertDrafts(
            listOf(
                dateRange(14, 15) to 0,
                dateRange(16, 20) to 1,
            )
        )

        sendAllFeeDecisions()

        assertFinal(
            listOf(
                Triple(FeeDecisionStatus.SENT, dateRange(10, 13), 1),
                Triple(FeeDecisionStatus.SENT, dateRange(16, 20), 1),
            )
        )
    }

    private fun day(d: Int) = LocalDate.of(2022, 6, d)
    private fun dateRange(f: Int, t: Int) = DateRange(day(f), day(t))

    private fun assertDrafts(expectedDrafts: List<Pair<DateRange, Int>>) {
        val decisions = getAllFeeDecisions()
        assertEquals(1 + expectedDrafts.size, decisions.size)
        assertEquals(sentDecision, decisions.find { it.status == FeeDecisionStatus.SENT })
        expectedDrafts.forEach { (range, children) ->
            assertTrue {
                decisions.any {
                    it.status == FeeDecisionStatus.DRAFT &&
                        it.validDuring == range &&
                        it.children.size == children
                }
            }
        }
    }

    private fun assertFinal(expectedFinalState: List<Triple<FeeDecisionStatus, DateRange, Int>>) {
        val sent = getAllFeeDecisions()
        assertEquals(expectedFinalState.size, sent.size)
        expectedFinalState.forEach { (status, range, children) ->
            assertTrue {
                sent.any {
                    it.status == status && it.validDuring == range && it.children.size == children
                }
            }
        }
    }

    private fun getAllFeeDecisions(): List<FeeDecision> {
        return db.read { tx -> tx.createQuery(feeDecisionQuery()).mapTo<FeeDecision>() }
            .sortedBy { it.validFrom }
    }

    private fun generate() {
        db.transaction { tx ->
            generator.generateNewDecisionsForAdult(
                tx,
                now,
                testAdult_1.id,
                originalRange.start.minusYears(5)
            )
        }
    }

    private fun sendAllFeeDecisions() {
        getAllFeeDecisions()
            .filter { it.status == FeeDecisionStatus.DRAFT }
            .map { it.id }
            .let { ids -> feeDecisionController.confirmDrafts(dbInstance(), admin, now, ids, null) }
        asyncJobRunner.runPendingJobsSync(now)
    }
}
