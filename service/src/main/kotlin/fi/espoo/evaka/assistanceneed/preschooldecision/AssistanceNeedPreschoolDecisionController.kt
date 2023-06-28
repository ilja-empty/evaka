// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later
package fi.espoo.evaka.assistanceneed.preschooldecision

import fi.espoo.evaka.Audit
import fi.espoo.evaka.assistanceneed.decision.AssistanceNeedDecisionStatus
import fi.espoo.evaka.pis.Employee
import fi.espoo.evaka.pis.getEmployees
import fi.espoo.evaka.pis.getEmployeesByRoles
import fi.espoo.evaka.shared.AssistanceNeedPreschoolDecisionId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.FeatureConfig
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.Forbidden
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AssistanceNeedPreschoolDecisionController(
    private val featureConfig: FeatureConfig,
    private val accessControl: AccessControl
) {
    @PostMapping("/children/{childId}/assistance-need-preschool-decisions")
    fun createAssistanceNeedPreschoolDecision(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable childId: ChildId
    ): AssistanceNeedPreschoolDecision {
        return db.connect { dbc ->
                dbc.transaction { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.Child.CREATE_ASSISTANCE_NEED_PRESCHOOL_DECISION,
                        childId
                    )

                    tx.insertEmptyAssistanceNeedPreschoolDecisionDraft(childId)
                }
            }
            .also { assistanceNeedDecision ->
                Audit.ChildAssistanceNeedPreschoolDecisionCreate.log(
                    targetId = childId,
                    objectId = assistanceNeedDecision.id
                )
            }
    }

    @GetMapping("/assistance-need-preschool-decisions/{id}")
    fun getAssistanceNeedPreschoolDecision(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: AssistanceNeedPreschoolDecisionId
    ): AssistanceNeedPreschoolDecisionResponse {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.AssistanceNeedPreschoolDecision.READ,
                        id
                    )
                    val decision = tx.getAssistanceNeedPreschoolDecisionById(id)

                    AssistanceNeedPreschoolDecisionResponse(
                        decision,
                        permittedActions = accessControl.getPermittedActions(tx, user, clock, id)
                    )
                }
            }
            .also { Audit.ChildAssistanceNeedPreschoolDecisionRead.log(targetId = id) }
    }

    @PutMapping("/assistance-need-preschool-decisions/{id}")
    fun updateAssistanceNeedPreschoolDecision(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: AssistanceNeedPreschoolDecisionId,
        @RequestBody body: AssistanceNeedPreschoolDecisionForm
    ) {
        return db.connect { dbc ->
                dbc.transaction { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.AssistanceNeedPreschoolDecision.UPDATE,
                        id
                    )
                    val decision = tx.getAssistanceNeedPreschoolDecisionById(id)

                    if (
                        decision.status != AssistanceNeedDecisionStatus.NEEDS_WORK &&
                            (decision.status != AssistanceNeedDecisionStatus.DRAFT ||
                                decision.sentForDecision != null)
                    ) {
                        throw Forbidden(
                            "Only non-sent draft or workable decisions can be edited",
                            "UNEDITABLE_DECISION"
                        )
                    }

                    tx.updateAssistanceNeedPreschoolDecision(id, body)
                }
            }
            .also { Audit.ChildAssistanceNeedPreschoolDecisionUpdate.log(targetId = id) }
    }

    @GetMapping("/children/{childId}/assistance-need-preschool-decisions")
    fun getAssistanceNeedPreschoolDecisions(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable childId: ChildId
    ): List<AssistanceNeedPreschoolDecisionBasicsResponse> {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.Child.READ_ASSISTANCE_NEED_PRESCHOOL_DECISIONS,
                        childId
                    )
                    val decisions = tx.getAssistanceNeedPreschoolDecisionsByChildId(childId)
                    val permittedActions =
                        accessControl.getPermittedActions<
                            AssistanceNeedPreschoolDecisionId,
                            Action.AssistanceNeedPreschoolDecision
                        >(
                            tx,
                            user,
                            clock,
                            decisions.map { it.id }
                        )
                    decisions.map {
                        AssistanceNeedPreschoolDecisionBasicsResponse(
                            decision = it,
                            permittedActions = permittedActions[it.id]!!
                        )
                    }
                }
            }
            .also {
                Audit.ChildAssistanceNeedPreschoolDecisionsList.log(
                    targetId = childId,
                    meta = mapOf("count" to it.size)
                )
            }
    }

    @DeleteMapping("/assistance-need-preschool-decisions/{id}")
    fun deleteAssistanceNeedPreschoolDecision(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: AssistanceNeedPreschoolDecisionId
    ) {
        return db.connect { dbc ->
                dbc.transaction { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.AssistanceNeedPreschoolDecision.DELETE,
                        id
                    )
                    if (!tx.deleteAssistanceNeedPreschoolDecision(id)) {
                        throw NotFound(
                            "Assistance need preschool decision $id cannot found or cannot be deleted",
                            "DECISION_NOT_FOUND"
                        )
                    }
                }
            }
            .also { Audit.ChildAssistanceNeedPreschoolDecisionDelete.log(targetId = id) }
    }

    @PutMapping("/assistance-need-preschool-decisions/{id}/decision-maker")
    fun updateAssistanceNeedPreschoolDecisionDecisionMaker(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: AssistanceNeedPreschoolDecisionId,
        @RequestBody body: UpdateDecisionMakerForAssistanceNeedPreschoolDecisionRequest
    ) {
        return db.connect { dbc ->
                dbc.transaction { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.AssistanceNeedPreschoolDecision.UPDATE_DECISION_MAKER,
                        id
                    )
                    val decision = tx.getAssistanceNeedPreschoolDecisionById(id)

                    if (
                        decision.status == AssistanceNeedDecisionStatus.ACCEPTED ||
                            decision.status == AssistanceNeedDecisionStatus.REJECTED ||
                            decision.status == AssistanceNeedDecisionStatus.ANNULLED ||
                            decision.sentForDecision == null
                    ) {
                        throw BadRequest(
                            "Decision maker cannot be changed for already-decided or unsent decisions"
                        )
                    }

                    tx.updateAssistanceNeedPreschoolDecision(
                        id,
                        decision.form.copy(
                            decisionMakerEmployeeId = EmployeeId(user.rawId()),
                            decisionMakerTitle = body.title
                        ),
                        decisionMakerHasOpened = true
                    )
                }
            }
            .also { Audit.ChildAssistanceNeedDecisionUpdateDecisionMaker.log(targetId = id) }
    }

    @GetMapping("/assistance-need-preschool-decisions/{id}/decision-maker-options")
    fun getAssistancePreschoolDecisionMakerOptions(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable id: AssistanceNeedPreschoolDecisionId,
    ): List<Employee> {
        return db.connect { dbc ->
                dbc.read { tx ->
                    accessControl.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.AssistanceNeedPreschoolDecision.READ_DECISION_MAKER_OPTIONS,
                        id
                    )
                    featureConfig.assistanceDecisionMakerRoles?.let { roles ->
                        val assistanceDecision = tx.getAssistanceNeedPreschoolDecisionById(id)
                        tx.getEmployeesByRoles(roles, assistanceDecision.form.selectedUnit)
                    }
                        ?: tx.getEmployees().sortedBy { it.email }
                }
            }
            .also {
                Audit.ChildAssistanceNeedPreschoolDecisionReadDecisionMakerOptions.log(
                    targetId = id,
                    meta = mapOf("count" to it.size)
                )
            }
    }

    data class AssistanceNeedPreschoolDecisionBasicsResponse(
        val decision: AssistanceNeedPreschoolDecisionBasics,
        val permittedActions: Set<Action.AssistanceNeedPreschoolDecision>
    )

    data class AssistanceNeedPreschoolDecisionResponse(
        val decision: AssistanceNeedPreschoolDecision,
        val permittedActions: Set<Action.AssistanceNeedPreschoolDecision>
    )

    data class UpdateDecisionMakerForAssistanceNeedPreschoolDecisionRequest(val title: String)
}
