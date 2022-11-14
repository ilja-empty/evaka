// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.assistanceaction

import fi.espoo.evaka.Audit
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.placement.getPlacementsForChild
import fi.espoo.evaka.shared.AssistanceActionId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
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
class AssistanceActionController(
    private val assistanceActionService: AssistanceActionService,
    private val accessControl: AccessControl
) {
    @PostMapping("/children/{childId}/assistance-actions")
    fun createAssistanceAction(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable childId: ChildId,
        @RequestBody body: AssistanceActionRequest
    ): AssistanceAction {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Child.CREATE_ASSISTANCE_ACTION,
                        childId
                    )
                }
                assistanceActionService.createAssistanceAction(
                    dbc,
                    user = user,
                    childId = childId,
                    data = body
                )
            }
            .also { assistanceAction ->
                Audit.ChildAssistanceActionCreate.log(
                    targetId = childId,
                    objectId = assistanceAction.id
                )
            }
    }

    @GetMapping("/children/{childId}/assistance-actions")
    fun getAssistanceActions(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable childId: ChildId
    ): List<AssistanceActionResponse> {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Child.READ_ASSISTANCE_ACTION,
                        childId
                    )
                }
                val relevantPreschoolPlacements =
                    dbc.read { tx ->
                        tx.getPlacementsForChild(childId).filter {
                            (it.type == PlacementType.PRESCHOOL ||
                                it.type == PlacementType.PRESCHOOL_DAYCARE) &&
                                it.startDate <= clock.today()
                        }
                    }
                val assistanceActions =
                    assistanceActionService.getAssistanceActionsByChildId(dbc, childId).let {
                        allAssistanceActions ->
                        val prePreschool =
                            allAssistanceActions.filterNot {
                                relevantPreschoolPlacements.isEmpty() ||
                                    relevantPreschoolPlacements.any { placement ->
                                        placement.startDate.isBefore(it.startDate) ||
                                            placement.startDate == it.startDate
                                    }
                            }
                        val decisions =
                            dbc.read { tx ->
                                accessControl.checkPermissionFor(
                                    tx,
                                    user,
                                    clock,
                                    Action.AssistanceAction.READ_PRE_PRESCHOOL_ASSISTANCE_ACTION,
                                    prePreschool.map { it.id }
                                )
                            }
                        allAssistanceActions.filter { decisions[it.id]?.isPermitted() ?: true }
                    }
                val assistanceActionIds = assistanceActions.map { it.id }
                val permittedActions =
                    dbc.read { tx ->
                        accessControl.getPermittedActions<
                            AssistanceActionId, Action.AssistanceAction
                        >(
                            tx,
                            user,
                            clock,
                            assistanceActionIds
                        )
                    }
                assistanceActions.map {
                    AssistanceActionResponse(it, permittedActions[it.id] ?: emptySet())
                }
            }
            .also {
                Audit.ChildAssistanceActionRead.log(
                    targetId = childId,
                    meta = mapOf("count" to it.size)
                )
            }
    }

    @PutMapping("/assistance-actions/{id}")
    fun updateAssistanceAction(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable("id") assistanceActionId: AssistanceActionId,
        @RequestBody body: AssistanceActionRequest
    ): AssistanceAction {
        return db.connect { dbc ->
                dbc.read {
                    accessControl.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.AssistanceAction.UPDATE,
                        assistanceActionId
                    )
                }
                assistanceActionService.updateAssistanceAction(
                    dbc,
                    user = user,
                    id = assistanceActionId,
                    data = body
                )
            }
            .also { Audit.ChildAssistanceActionUpdate.log(targetId = assistanceActionId) }
    }

    @DeleteMapping("/assistance-actions/{id}")
    fun deleteAssistanceAction(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @PathVariable("id") assistanceActionId: AssistanceActionId
    ) {
        db.connect { dbc ->
            dbc.read {
                accessControl.requirePermissionFor(
                    it,
                    user,
                    clock,
                    Action.AssistanceAction.DELETE,
                    assistanceActionId
                )
            }
            assistanceActionService.deleteAssistanceAction(dbc, assistanceActionId)
        }
        Audit.ChildAssistanceActionDelete.log(targetId = assistanceActionId)
    }

    @GetMapping("/assistance-action-options")
    fun getAssistanceActionOptions(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock
    ): List<AssistanceActionOption> {
        return db.connect { dbc ->
            dbc.read {
                accessControl.requirePermissionFor(
                    it,
                    user,
                    clock,
                    Action.Global.READ_ASSISTANCE_ACTION_OPTIONS
                )
            }
            assistanceActionService.getAssistanceActionOptions(dbc)
        }
    }
}
