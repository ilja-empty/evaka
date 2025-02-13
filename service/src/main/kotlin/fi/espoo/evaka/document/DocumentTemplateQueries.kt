// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.document

import fi.espoo.evaka.shared.DocumentTemplateId
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.DateRange

fun Database.Transaction.insertTemplate(template: DocumentTemplateCreateRequest): DocumentTemplate {
    return createQuery(
            """
        INSERT INTO document_template (name, type, language, confidential, legal_basis, validity, content) 
        VALUES (:name, :type, :language, :confidential, :legalBasis, :validity, :content::jsonb)
        RETURNING *
    """
                .trimIndent()
        )
        .bindKotlin(template)
        .bind("content", DocumentTemplateContent(sections = emptyList()))
        .mapTo<DocumentTemplate>()
        .exactlyOne()
}

fun Database.Transaction.duplicateTemplate(
    id: DocumentTemplateId,
    template: DocumentTemplateCreateRequest
): DocumentTemplate {
    return createQuery(
            """
        INSERT INTO document_template (name, type, language, confidential, legal_basis, validity, content) 
        SELECT :name, :type, :language, :confidential, :legalBasis, :validity, content FROM document_template WHERE id = :id
        RETURNING *
    """
                .trimIndent()
        )
        .bind("id", id)
        .bindKotlin(template)
        .mapTo<DocumentTemplate>()
        .exactlyOne()
}

fun Database.Read.getTemplateSummaries(): List<DocumentTemplateSummary> {
    return createQuery(
            """
        SELECT id, name, type, language, validity, published
        FROM document_template
    """
                .trimIndent()
        )
        .mapTo<DocumentTemplateSummary>()
        .toList()
}

fun Database.Read.getTemplate(id: DocumentTemplateId): DocumentTemplate? {
    return createQuery("SELECT * FROM document_template WHERE id = :id")
        .bind("id", id)
        .exactlyOneOrNull<DocumentTemplate>()
}

fun Database.Transaction.updateDraftTemplateContent(
    id: DocumentTemplateId,
    content: DocumentTemplateContent
) {
    createUpdate(
            """
        UPDATE document_template
        SET content = :content
        WHERE id = :id AND published = false
    """
                .trimIndent()
        )
        .bind("id", id)
        .bind("content", content)
        .updateExactlyOne()
}

fun Database.Transaction.updateTemplateValidity(id: DocumentTemplateId, validity: DateRange) {
    createUpdate(
            """
        UPDATE document_template
        SET validity = :validity
        WHERE id = :id
    """
                .trimIndent()
        )
        .bind("id", id)
        .bind("validity", validity)
        .updateExactlyOne()
}

fun Database.Transaction.publishTemplate(id: DocumentTemplateId) {
    createUpdate(
            """
        UPDATE document_template
        SET published = true
        WHERE id = :id
    """
                .trimIndent()
        )
        .bind("id", id)
        .updateExactlyOne()
}

fun Database.Transaction.deleteDraftTemplate(id: DocumentTemplateId) {
    createUpdate(
            """
        DELETE FROM document_template 
        WHERE id = :id AND published = false
    """
                .trimIndent()
        )
        .bind("id", id)
        .updateExactlyOne()
}
