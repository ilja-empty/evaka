// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.incomestatement

import fi.espoo.evaka.Audit
import fi.espoo.evaka.daycare.domain.ProviderType
import fi.espoo.evaka.invoicing.controller.SortDirection
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.IncomeStatementId
import fi.espoo.evaka.shared.Paged
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/income-statements")
class IncomeStatementController(private val accessControl: AccessControl) {
    @GetMapping("/person/{personId}")
    fun getIncomeStatements(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable personId: PersonId,
        @RequestParam page: Int,
        @RequestParam pageSize: Int
    ): Paged<IncomeStatement> {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Person.READ_INCOME_STATEMENTS,
                        personId
                    )
                    it.readIncomeStatementsForPerson(
                        personId = personId,
                        includeEmployeeContent = true,
                        page = page,
                        pageSize = pageSize
                    )
                }
            }
            .also {
                Audit.IncomeStatementsOfPerson.log(
                    targetId = personId,
                    meta = mapOf("total" to it.total)
                )
            }
    }

    @GetMapping("/child/{childId}")
    fun getChildIncomeStatements(
        db: Database,
        user: AuthenticatedUser.Citizen,
        clock: EvakaClock,
        @PathVariable childId: ChildId,
        @RequestParam page: Int,
        @RequestParam pageSize: Int
    ): Paged<IncomeStatement> {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.Person.READ_INCOME_STATEMENTS,
                        PersonId(childId.raw)
                    )
                    tx.readIncomeStatementsForPerson(
                        PersonId(childId.raw),
                        includeEmployeeContent = true,
                        page = page,
                        pageSize = pageSize
                    )
                }
            }
            .also {
                Audit.IncomeStatementsOfChild.log(
                    targetId = childId,
                    meta = mapOf("total" to it.total)
                )
            }
    }

    @GetMapping("/person/{personId}/{incomeStatementId}")
    fun getIncomeStatement(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable personId: PersonId,
        @PathVariable incomeStatementId: IncomeStatementId
    ): IncomeStatement {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Person.READ_INCOME_STATEMENTS,
                        personId
                    )
                    it.readIncomeStatementForPerson(
                        personId,
                        incomeStatementId,
                        includeEmployeeContent = true
                    )
                }
                    ?: throw NotFound("No such income statement")
            }
            .also { Audit.IncomeStatementReadOfPerson.log(targetId = incomeStatementId) }
    }

    data class SetIncomeStatementHandledBody(val handled: Boolean, val handlerNote: String)

    @PostMapping("/{incomeStatementId}/handled")
    fun setHandled(
        db: Database,
        user: AuthenticatedUser.Employee,
        clock: EvakaClock,
        @PathVariable incomeStatementId: IncomeStatementId,
        @RequestBody body: SetIncomeStatementHandledBody
    ) {
        db.connect { dbc ->
            dbc.transaction { tx ->
                accessControl.requirePermissionFor(
                    tx,
                    user,
                    clock,
                    Action.IncomeStatement.UPDATE_HANDLED,
                    incomeStatementId
                )
                tx.updateIncomeStatementHandled(
                    incomeStatementId,
                    body.handlerNote,
                    if (body.handled) user.id else null
                )
            }
        }
        Audit.IncomeStatementUpdateHandled.log(targetId = incomeStatementId)
    }

    @PostMapping("/awaiting-handler")
    fun getIncomeStatementsAwaitingHandler(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestBody body: SearchIncomeStatementsRequest
    ): Paged<IncomeStatementAwaitingHandler> {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Global.FETCH_INCOME_STATEMENTS_AWAITING_HANDLER
                    )
                    it.fetchIncomeStatementsAwaitingHandler(
                        clock.now().toLocalDate(),
                        body.areas ?: emptyList(),
                        body.providerTypes ?: emptyList(),
                        body.sentStartDate,
                        body.sentEndDate,
                        body.placementValidDate,
                        body.page,
                        body.pageSize,
                        body.sortBy ?: IncomeStatementSortParam.CREATED,
                        body.sortDirection ?: SortDirection.ASC
                    )
                }
            }
            .also { Audit.IncomeStatementsAwaitingHandler.log(meta = mapOf("total" to it.total)) }
    }

    @GetMapping("/guardian/{guardianId}/children")
    fun getIncomeStatementChildren(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable guardianId: PersonId
    ): List<ChildBasicInfo> {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Person.READ_INCOME_STATEMENTS,
                        guardianId
                    )
                    it.getIncomeStatementChildrenByGuardian(guardianId, clock.today())
                }
            }
            .also {
                Audit.GuardianChildrenRead.log(
                    targetId = guardianId,
                    meta = mapOf("count" to it.size)
                )
            }
    }
}

data class SearchIncomeStatementsRequest(
    val page: Int = 1,
    val pageSize: Int = 50,
    val sortBy: IncomeStatementSortParam? = null,
    val sortDirection: SortDirection? = null,
    val areas: List<String>? = emptyList(),
    val providerTypes: List<ProviderType>? = emptyList(),
    val sentStartDate: LocalDate? = null,
    val sentEndDate: LocalDate? = null,
    val placementValidDate: LocalDate? = null
)

enum class IncomeStatementSortParam {
    CREATED,
    START_DATE
}
