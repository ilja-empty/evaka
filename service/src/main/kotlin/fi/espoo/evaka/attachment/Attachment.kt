// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attachment

import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.FeeAlterationId
import fi.espoo.evaka.shared.IncomeId
import fi.espoo.evaka.shared.IncomeStatementId
import fi.espoo.evaka.shared.MessageContentId
import fi.espoo.evaka.shared.MessageDraftId
import fi.espoo.evaka.shared.PedagogicalDocumentId

sealed class AttachmentParent {
    data class Application(val applicationId: ApplicationId) : AttachmentParent()

    data class IncomeStatement(val incomeStatementId: IncomeStatementId) : AttachmentParent()

    data class Income(val incomeId: IncomeId) : AttachmentParent()

    data class MessageDraft(val draftId: MessageDraftId) : AttachmentParent()

    data class MessageContent(val messageContentId: MessageContentId) : AttachmentParent()

    data class PedagogicalDocument(val pedagogicalDocumentId: PedagogicalDocumentId) :
        AttachmentParent()

    data class FeeAlteration(val feeAlterationId: FeeAlterationId) : AttachmentParent()

    object None : AttachmentParent()
}

data class Attachment(
    val id: AttachmentId,
    val name: String,
    val contentType: String,
    val attachedTo: AttachmentParent
)

data class IncomeAttachment(val id: AttachmentId, val name: String, val contentType: String)

data class MessageAttachment(val id: AttachmentId, val name: String, val contentType: String)

enum class AttachmentType {
    URGENCY,
    EXTENDED_CARE
}
