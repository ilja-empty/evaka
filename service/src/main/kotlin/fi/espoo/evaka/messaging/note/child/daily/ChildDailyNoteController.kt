// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.messaging.note.child.daily

import fi.espoo.evaka.Audit
import fi.espoo.evaka.shared.ChildDailyNoteId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ChildDailyNoteController(
    private val ac: AccessControl
) {
    @PostMapping("/children/{childId}/child-daily-notes")
    fun createChildDailyNote(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable childId: UUID,
        @RequestBody body: ChildDailyNoteBody
    ): ChildDailyNoteId {
        Audit.ChildDailyNoteCreate.log(childId)
        ac.requirePermissionFor(user, Action.Child.CREATE_DAILY_NOTE, childId)

        return db.transaction { it.createChildDailyNote(user, childId, body) }
    }

    @PutMapping("/child-daily-notes/{noteId}")
    fun updateChildDailyNote(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable noteId: ChildDailyNoteId,
        @RequestBody body: ChildDailyNoteBody
    ): ChildDailyNote {
        Audit.ChildDailyNoteUpdate.log(noteId, noteId)
        ac.requirePermissionFor(user, Action.ChildDailyNote.UPDATE, noteId)

        return db.transaction { it.updateChildDailyNote(user, noteId, body) }
    }

    @DeleteMapping("/child-daily-notes/{noteId}")
    fun deleteChildDailyNote(
        db: Database.Connection,
        user: AuthenticatedUser,
        @PathVariable noteId: ChildDailyNoteId
    ) {
        Audit.ChildDailyNoteDelete.log(noteId)
        ac.requirePermissionFor(user, Action.ChildDailyNote.DELETE, noteId)

        return db.transaction { it.deleteChildDailyNote(noteId) }
    }
}
