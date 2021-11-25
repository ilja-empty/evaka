// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.assistanceneed

import fi.espoo.evaka.Audit
import fi.espoo.evaka.daycare.controllers.utils.created
import fi.espoo.evaka.daycare.controllers.utils.noContent
import fi.espoo.evaka.daycare.controllers.utils.ok
import fi.espoo.evaka.pis.service.PersonService
import fi.espoo.evaka.shared.AssistanceNeedId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class AssistanceNeedController(
    private val assistanceNeedService: AssistanceNeedService,
    private val accessControl: AccessControl,
    private val personService: PersonService
) {
    @PostMapping("/children/{childId}/assistance-needs")
    fun createAssistanceNeed(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable childId: UUID,
        @RequestBody body: AssistanceNeedRequest
    ): ResponseEntity<AssistanceNeed> {
        Audit.ChildAssistanceNeedCreate.log(targetId = childId)
        accessControl.requirePermissionFor(user, Action.Child.CREATE_ASSISTANCE_NEED, childId)
        return assistanceNeedService.createAssistanceNeed(
            db,
            user = user,
            childId = childId,
            data = body
        ).let { created(it, URI.create("/children/$childId/assistance-needs/${it.id}")) }
    }

    @GetMapping("/children/{childId}/assistance-needs")
    fun getAssistanceNeeds(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable childId: UUID
    ): List<AssistanceNeed> {
        Audit.ChildAssistanceNeedRead.log(targetId = childId)
        accessControl.requirePermissionFor(user, Action.Child.READ_ASSISTANCE_NEED, childId)
        return assistanceNeedService.getAssistanceNeedsByChildId(db, childId).filter { assistanceNeed ->
            accessControl.hasPermissionFor(user, Action.AssistanceNeed.READ_PRE_PRESCHOOL_ASSISTANCE_NEED, assistanceNeed.id)
        }
    }

    @PutMapping("/assistance-needs/{id}")
    fun updateAssistanceNeed(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable("id") assistanceNeedId: AssistanceNeedId,
        @RequestBody body: AssistanceNeedRequest
    ): ResponseEntity<AssistanceNeed> {
        Audit.ChildAssistanceNeedUpdate.log(targetId = assistanceNeedId)
        accessControl.requirePermissionFor(user, Action.AssistanceNeed.UPDATE, assistanceNeedId)
        return assistanceNeedService.updateAssistanceNeed(
            db,
            user = user,
            id = assistanceNeedId,
            data = body
        ).let(::ok)
    }

    @DeleteMapping("/assistance-needs/{id}")
    fun deleteAssistanceNeed(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable("id") assistanceNeedId: AssistanceNeedId
    ): ResponseEntity<Unit> {
        Audit.ChildAssistanceNeedDelete.log(targetId = assistanceNeedId)
        accessControl.requirePermissionFor(user, Action.AssistanceNeed.DELETE, assistanceNeedId)
        assistanceNeedService.deleteAssistanceNeed(db, assistanceNeedId)
        return noContent()
    }

    @GetMapping("/assistance-basis-options")
    fun getAssistanceBasisOptions(db: Database.Connection, user: AuthenticatedUser): List<AssistanceBasisOption> {
        user.requireAnyEmployee()
        return assistanceNeedService.getAssistanceBasisOptions(db)
    }
}
