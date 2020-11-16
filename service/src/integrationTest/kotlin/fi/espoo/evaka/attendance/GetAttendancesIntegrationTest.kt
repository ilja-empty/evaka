// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attendance

import com.github.kittinunf.fuel.jackson.responseObject
import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.daycare.service.AbsenceType
import fi.espoo.evaka.daycare.service.CareType
import fi.espoo.evaka.insertGeneralTestFixtures
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.resetDatabase
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.auth.asUser
import fi.espoo.evaka.shared.db.handle
import fi.espoo.evaka.shared.dev.DevDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestAbsence
import fi.espoo.evaka.shared.dev.insertTestChildAttendance
import fi.espoo.evaka.shared.dev.insertTestDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestDaycareGroupPlacement
import fi.espoo.evaka.shared.dev.insertTestPlacement
import fi.espoo.evaka.shared.dev.updateDaycareAclWithEmployee
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDecisionMaker_1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class GetAttendancesIntegrationTest : FullApplicationTest() {
    private val staffUser = AuthenticatedUser(testDecisionMaker_1.id, emptySet())
    private val groupId = UUID.randomUUID()
    private val groupName = "Testaajat"
    private val daycarePlacementId = UUID.randomUUID()
    private val placementStart = LocalDate.now().minusDays(30)
    private val placementEnd = LocalDate.now().plusDays(30)

    @BeforeEach
    fun beforeEach() {
        jdbi.handle { h ->
            resetDatabase(h)
            insertGeneralTestFixtures(h)
            h.insertTestDaycareGroup(DevDaycareGroup(id = groupId, daycareId = testDaycare.id, name = groupName))
            insertTestPlacement(
                h = h,
                id = daycarePlacementId,
                childId = testChild_1.id,
                unitId = testDaycare.id,
                startDate = placementStart,
                endDate = placementEnd,
                type = PlacementType.PRESCHOOL_DAYCARE
            )
            insertTestDaycareGroupPlacement(
                h = h,
                daycarePlacementId = daycarePlacementId,
                groupId = groupId,
                startDate = placementStart,
                endDate = placementEnd
            )
            updateDaycareAclWithEmployee(h, testDaycare.id, staffUser.id, UserRole.STAFF)
        }
    }

    @Test
    fun `unit info is correct`() {
        val response = fetchAttendances()
        assertEquals(testDaycare.name, response.unit.name)
        assertEquals(1, response.unit.groups.size)
        assertEquals(groupId, response.unit.groups.first().id)
        assertEquals(groupName, response.unit.groups.first().name)
    }

    @Test
    fun `child is coming`() {
        jdbi.handle {
            insertTestChildAttendance(
                h = it,
                childId = testChild_1.id,
                unitId = testDaycare.id,
                arrived = OffsetDateTime.now().minusDays(1).minusHours(8).toInstant(),
                departed = OffsetDateTime.now().minusDays(1).toInstant()
            )
        }
        val child = expectOneChild()
        assertEquals(AttendanceStatus.COMING, child.status)
        assertNull(child.attendance)
        assertEquals(0, child.absences.size)
    }

    @Test
    fun `child is present`() {
        val arrived = OffsetDateTime.now().minusHours(3).toInstant()
        jdbi.handle {
            insertTestChildAttendance(
                h = it,
                childId = testChild_1.id,
                unitId = testDaycare.id,
                arrived = arrived,
                departed = null
            )
        }
        val child = expectOneChild()
        assertEquals(AttendanceStatus.PRESENT, child.status)
        assertNotNull(child.attendance)
        assertEquals(arrived, child.attendance!!.arrived)
        assertNull(child.attendance!!.departed)
        assertEquals(testDaycare.id, child.attendance!!.unitId)
        assertEquals(0, child.absences.size)
    }

    @Test
    fun `child has departed`() {
        val arrived = OffsetDateTime.now().minusHours(3).toInstant()
        val departed = OffsetDateTime.now().minusMinutes(1).toInstant()
        jdbi.handle {
            insertTestChildAttendance(
                h = it,
                childId = testChild_1.id,
                unitId = testDaycare.id,
                arrived = arrived,
                departed = departed
            )
        }
        val child = expectOneChild()
        assertEquals(AttendanceStatus.DEPARTED, child.status)
        assertNotNull(child.attendance)
        assertEquals(arrived, child.attendance!!.arrived)
        assertEquals(departed, child.attendance!!.departed)
        assertEquals(testDaycare.id, child.attendance!!.unitId)
        assertEquals(0, child.absences.size)
    }

    @Test
    fun `child is absent`() {
        jdbi.handle {
            insertTestAbsence(
                h = it,
                childId = testChild_1.id,
                careType = CareType.PRESCHOOL,
                date = LocalDate.now(),
                absenceType = AbsenceType.SICKLEAVE
            )
            insertTestAbsence(
                h = it,
                childId = testChild_1.id,
                careType = CareType.PRESCHOOL_DAYCARE,
                date = LocalDate.now(),
                absenceType = AbsenceType.SICKLEAVE
            )
        }
        val child = expectOneChild()
        assertEquals(AttendanceStatus.ABSENT, child.status)
        assertNull(child.attendance)
        assertEquals(2, child.absences.size)
        assertEquals(1, child.absences.filter { it.careType == CareType.PRESCHOOL && it.absenceType == AbsenceType.SICKLEAVE }.size)
        assertEquals(1, child.absences.filter { it.careType == CareType.PRESCHOOL_DAYCARE && it.absenceType == AbsenceType.SICKLEAVE }.size)
    }

    private fun fetchAttendances(): AttendanceResponse {
        val (_, res, result) = http.get("/attendances/units/${testDaycare.id}")
            .asUser(staffUser)
            .responseObject<AttendanceResponse>(objectMapper)

        assertEquals(200, res.statusCode)
        return result.get()
    }

    private fun expectOneChild(): Child {
        val response = fetchAttendances()
        assertEquals(1, response.children.size)
        return response.children.first()
    }
}
