// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.emailclient

interface IEmailMessageProvider {

    val subjectForClubApplicationReceivedEmail: String
    val subjectForDaycareApplicationReceivedEmail: String
    val subjectForPreschoolApplicationReceivedEmail: String
    val subjectForDecisionEmail: String

    fun getDecisionEmailSubject(): String {
        return subjectForDecisionEmail + getMessagePostfix()
    }

    fun getDecisionEmailHtml(): String
    fun getDecisionEmailText(): String

    fun getClubApplicationReceivedEmailSubject(): String {
        return subjectForClubApplicationReceivedEmail + getMessagePostfix()
    }

    fun getClubApplicationReceivedEmailHtml(): String
    fun getClubApplicationReceivedEmailText(): String

    fun getDaycareApplicationReceivedEmailSubject(): String {
        return subjectForDaycareApplicationReceivedEmail + getMessagePostfix()
    }

    fun getDaycareApplicationReceivedEmailHtml(): String
    fun getDaycareApplicationReceivedEmailText(): String

    fun getPreschoolApplicationReceivedEmailSubject(): String {
        return subjectForPreschoolApplicationReceivedEmail + getMessagePostfix()
    }

    fun getPreschoolApplicationReceivedEmailHtml(withinApplicationPeriod: Boolean): String
    fun getPreschoolApplicationReceivedEmailText(withinApplicationPeriod: Boolean): String

    private fun getMessagePostfix(): String {
        return if (System.getenv("VOLTTI_ENV") == "staging") " [staging]" else ""
    }
}
