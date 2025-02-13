// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pis.controller

import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.pis.FamilyContactRole
import fi.espoo.evaka.pis.controllers.FamilyController
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.dev.DevChild
import fi.espoo.evaka.shared.dev.DevEmployee
import fi.espoo.evaka.shared.dev.DevFosterParent
import fi.espoo.evaka.shared.dev.DevFridgeChild
import fi.espoo.evaka.shared.dev.DevFridgePartnership
import fi.espoo.evaka.shared.dev.DevGuardian
import fi.espoo.evaka.shared.dev.DevPerson
import fi.espoo.evaka.shared.dev.insertFosterParent
import fi.espoo.evaka.shared.dev.insertFridgeChild
import fi.espoo.evaka.shared.dev.insertFridgePartnership
import fi.espoo.evaka.shared.dev.insertTestChild
import fi.espoo.evaka.shared.dev.insertTestEmployee
import fi.espoo.evaka.shared.dev.insertTestGuardian
import fi.espoo.evaka.shared.dev.insertTestPerson
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.domain.MockEvakaClock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FamilyControllerTest : FullApplicationTest(resetDbBeforeEach = true) {
    @Autowired lateinit var controller: FamilyController

    private lateinit var user: AuthenticatedUser.Employee
    private val clock =
        MockEvakaClock(HelsinkiDateTime.of(LocalDate.of(2022, 11, 1), LocalTime.of(15, 0)))
    private val currentlyValid =
        FiniteDateRange(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31))

    private lateinit var child: ChildId

    @BeforeEach
    fun beforeEach() {
        db.transaction { tx ->
            user =
                AuthenticatedUser.Employee(
                    tx.insertTestEmployee(DevEmployee(roles = setOf(UserRole.ADMIN))),
                    roles = setOf(UserRole.ADMIN)
                )
            child = tx.insertTestPerson(DevPerson()).also { tx.insertTestChild(DevChild(it)) }
        }
    }

    @Test
    fun `getFamilyContactsSummary, full family with guardian as head of family`() {
        lateinit var guardian: PersonId
        lateinit var guardianAndHouseholdHead: PersonId
        lateinit var fosterParent: PersonId
        lateinit var householdSibling: PersonId
        lateinit var householdAdult: PersonId

        db.transaction { tx ->
            guardian =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertTestGuardian(DevGuardian(it, child))
                }
            guardianAndHouseholdHead =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertTestGuardian(DevGuardian(it, child))
                    tx.insertFridgeChild(
                        DevFridgeChild(
                            childId = child,
                            headOfChild = it,
                            startDate = currentlyValid.start,
                            endDate = currentlyValid.end
                        )
                    )
                }
            fosterParent =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertFosterParent(
                        DevFosterParent(
                            childId = child,
                            parentId = it,
                            validDuring = currentlyValid.asDateRange()
                        )
                    )
                }
            householdSibling =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertFridgeChild(
                        DevFridgeChild(
                            childId = it,
                            headOfChild = guardianAndHouseholdHead,
                            startDate = currentlyValid.start,
                            endDate = currentlyValid.end
                        )
                    )
                }
            householdAdult =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertFridgePartnership(
                        DevFridgePartnership(
                            first = guardianAndHouseholdHead,
                            second = it,
                            startDate = currentlyValid.start,
                            endDate = currentlyValid.end
                        )
                    )
                }
        }
        assertEquals(
            listOf(
                (guardianAndHouseholdHead to FamilyContactRole.LOCAL_GUARDIAN),
                (guardian to FamilyContactRole.REMOTE_GUARDIAN),
                (fosterParent to FamilyContactRole.REMOTE_FOSTER_PARENT),
                (householdAdult to FamilyContactRole.LOCAL_ADULT),
                (householdSibling to FamilyContactRole.LOCAL_SIBLING)
            ),
            getFamilyContactSummary().map { (it.id to it.role) }
        )
    }

    @Test
    fun `getFamilyContactsSummary, minimal family with foster parent as head of family`() {
        lateinit var fosterParentAndHouseholdHead: PersonId

        db.transaction { tx ->
            fosterParentAndHouseholdHead =
                tx.insertTestPerson(DevPerson()).also {
                    tx.insertFosterParent(
                        DevFosterParent(
                            childId = child,
                            parentId = it,
                            validDuring = currentlyValid.asDateRange()
                        )
                    )
                    tx.insertFridgeChild(
                        DevFridgeChild(
                            childId = child,
                            headOfChild = it,
                            startDate = currentlyValid.start,
                            endDate = currentlyValid.end
                        )
                    )
                }
        }
        assertEquals(
            listOf(
                (fosterParentAndHouseholdHead to FamilyContactRole.LOCAL_FOSTER_PARENT),
            ),
            getFamilyContactSummary().map { (it.id to it.role) }
        )
    }

    private fun getFamilyContactSummary() =
        controller.getFamilyContactSummary(dbInstance(), user, clock, child)
}
