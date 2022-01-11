// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.service

import fi.espoo.evaka.decision.DecisionSendAddress
import fi.espoo.evaka.invoicing.domain.EmployeeWithName
import fi.espoo.evaka.invoicing.domain.FeeDecisionChildDetailed
import fi.espoo.evaka.invoicing.domain.FeeDecisionDetailed
import fi.espoo.evaka.invoicing.domain.FeeDecisionType
import fi.espoo.evaka.invoicing.domain.IncomeEffect
import fi.espoo.evaka.invoicing.domain.PersonDetailed
import fi.espoo.evaka.invoicing.domain.UnitData
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionDetailed
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionPlacementDetailed
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionServiceNeed
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionStatus
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionType
import fi.espoo.evaka.invoicing.testDecision1
import fi.espoo.evaka.invoicing.testDecisionIncome
import fi.espoo.evaka.invoicing.testFeeThresholds
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.shared.AreaId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.VoucherValueDecisionId
import fi.espoo.evaka.shared.config.PDFConfig
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.message.EvakaMessageProvider
import fi.espoo.evaka.shared.template.EvakaTemplateProvider
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PdfServiceTest {
    private val service: PDFService =
        PDFService(EvakaMessageProvider(), EvakaTemplateProvider(), PDFConfig.templateEngine())

    private val normalDecision = FeeDecisionDetailed(
        id = testDecision1.id,
        status = testDecision1.status,
        decisionNumber = testDecision1.decisionNumber,
        decisionType = FeeDecisionType.NORMAL,
        validDuring = testDecision1.validDuring,
        headOfFamily = PersonDetailed(
            id = PersonId(UUID.randomUUID()),
            dateOfBirth = LocalDate.of(1980, 1, 1),
            firstName = "John",
            lastName = "Doe",
            streetAddress = "Kamreerintie 2",
            postalCode = "02770",
            postOffice = "Espoo",
            restrictedDetailsEnabled = false
        ),
        partner = PersonDetailed(
            id = PersonId(UUID.randomUUID()),
            dateOfBirth = LocalDate.of(1980, 1, 1),
            firstName = "Joan",
            lastName = "Doe",
            streetAddress = "Kamreerintie 2",
            postalCode = "02770",
            postOffice = "Espoo",
            restrictedDetailsEnabled = false
        ),
        headOfFamilyIncome = testDecisionIncome.copy(total = 214159),
        partnerIncome = testDecisionIncome.copy(total = 413195),
        familySize = 3,
        feeThresholds = testFeeThresholds.getFeeDecisionThresholds(3),
        approvedAt = HelsinkiDateTime.of(LocalDate.of(2019, 4, 15), LocalTime.of(10, 15, 30)),
        approvedBy = EmployeeWithName(
            EmployeeId(UUID.randomUUID()),
            "Pirkko",
            "Päättäjä"
        ),
        children = testDecision1.children.map {
            FeeDecisionChildDetailed(
                child = PersonDetailed(
                    id = PersonId(UUID.randomUUID()),
                    dateOfBirth = LocalDate.of(2017, 1, 1),
                    firstName = "Johnny",
                    lastName = "Doe",
                    restrictedDetailsEnabled = false
                ),
                placementType = it.placement.type,
                placementUnit = UnitData(
                    id = DaycareId(UUID.randomUUID()),
                    name = "Leppäkerttu-konserni, päiväkoti Pupu Tupuna",
                    language = "fi",
                    areaId = AreaId(UUID.randomUUID()),
                    areaName = "Test Area"
                ),
                serviceNeedFeeCoefficient = it.serviceNeed.feeCoefficient,
                serviceNeedDescriptionFi = it.serviceNeed.descriptionFi,
                serviceNeedDescriptionSv = it.serviceNeed.descriptionSv,
                serviceNeedMissing = it.serviceNeed.missing,
                baseFee = it.baseFee,
                siblingDiscount = it.siblingDiscount,
                fee = it.fee,
                feeAlterations = it.feeAlterations,
                finalFee = it.finalFee
            )
        },
        financeDecisionHandlerFirstName = null,
        financeDecisionHandlerLastName = null
    )

    private val reliefDecision = normalDecision.copy(decisionType = FeeDecisionType.RELIEF_ACCEPTED)

    private val normalVoucherValueDecision = VoucherValueDecisionDetailed(
        id = VoucherValueDecisionId(testDecision1.id.raw),
        approvedAt = HelsinkiDateTime.of(LocalDate.of(2019, 4, 15), LocalTime.of(10, 15, 30)),
        approvedBy = EmployeeWithName(
            EmployeeId(UUID.randomUUID()),
            "Erkki",
            "Pelimerkki"
        ),
        decisionNumber = testDecision1.decisionNumber,
        decisionType = VoucherValueDecisionType.NORMAL,
        status = VoucherValueDecisionStatus.WAITING_FOR_SENDING,
        headOfFamily = PersonDetailed(
            id = PersonId(UUID.randomUUID()),
            dateOfBirth = LocalDate.of(1980, 1, 1),
            firstName = "Anselmi Aataminpoika",
            lastName = "Guggenheim",
            streetAddress = "Huhdannevanpolku 24 A 3",
            postalCode = "02770",
            postOffice = "Espoo",
            restrictedDetailsEnabled = false
        ),
        partner = PersonDetailed(
            id = PersonId(UUID.randomUUID()),
            dateOfBirth = LocalDate.of(1980, 1, 1),
            firstName = "Cynthia Elisabeth",
            lastName = "Maalahti-Guggenheim",
            streetAddress = "Huhdannevanpolku 24 A 3",
            postalCode = "02770",
            postOffice = "Espoo",
            restrictedDetailsEnabled = false
        ),
        validFrom = LocalDate.of(2020, 1, 1),
        validTo = null,
        financeDecisionHandlerFirstName = null,
        financeDecisionHandlerLastName = null,
        familySize = 3,
        feeThresholds = testFeeThresholds.getFeeDecisionThresholds(3),
        headOfFamilyIncome = testDecisionIncome.copy(effect = IncomeEffect.MAX_FEE_ACCEPTED, total = 214159),
        partnerIncome = testDecisionIncome.copy(effect = IncomeEffect.NOT_AVAILABLE, total = 413195),
        child = PersonDetailed(
            id = PersonId(UUID.randomUUID()),
            dateOfBirth = LocalDate.of(2017, 1, 1),
            firstName = "Iisakki Anselminpoika",
            lastName = "Guggenheim",
            restrictedDetailsEnabled = false
        ),
        childAge = 3,
        placement = VoucherValueDecisionPlacementDetailed(
            UnitData(
                id = DaycareId(UUID.randomUUID()),
                name = "Test Daycare",
                language = "fi",
                areaId = AreaId(UUID.randomUUID()),
                areaName = "Test Area"
            ),
            PlacementType.DAYCARE
        ),
        serviceNeed = VoucherValueDecisionServiceNeed(
            feeCoefficient = BigDecimal("1.00"),
            voucherValueCoefficient = BigDecimal("1.00"),
            feeDescriptionFi = "palveluntarve puuttuu, korkein maksu",
            feeDescriptionSv = "vårdbehovet saknas, högsta avgift",
            voucherValueDescriptionFi = "yli 25h/viikko",
            voucherValueDescriptionSv = "mer än 25 h/vecka"
        ),
        voucherValue = 120000,
        ageCoefficient = BigDecimal("1.00"),
        capacityFactor = BigDecimal("1"),
        baseCoPayment = 900,
        baseValue = 90000,
        coPayment = 12000,
        feeAlterations = emptyList(),
        finalCoPayment = 12000,
        siblingDiscount = 0
    )

    @Test
    fun `variables are ok with normal decision`() {
        val feeDecisionPdfData = FeeDecisionPdfData(
            decision = normalDecision,
            settings = mapOf(),
            lang = DocumentLang.fi.name
        )

        val simpleVariables = service.getFeeDecisionPdfVariables(feeDecisionPdfData).filterKeys {
            it != "parts"
        }

        val expectedSendAddress = DecisionSendAddress(
            "Kamreerintie 2",
            "02770",
            "Espoo",
            "Kamreerintie 2",
            "02770 Espoo",
            ""
        )

        val expected = mapOf(
            "approvedAt" to "15.04.2019",
            "decisionNumber" to 1010101010L,
            "decisionType" to "NORMAL",
            "isReliefDecision" to false,
            "hasPartner" to true,
            "headFullName" to "John Doe",
            "headIncomeEffect" to "INCOME",
            "headIncomeTotal" to "2141,59",
            "partnerFullName" to "Joan Doe",
            "partnerIncomeEffect" to "INCOME",
            "partnerIncomeTotal" to "4131,95",
            "sendAddress" to expectedSendAddress,
            "totalFee" to "434,00",
            "validFor" to "01.05.2019 - 31.05.2019",
            "validFrom" to "01.05.2019",
            "validTo" to "31.05.2019",
            "totalIncome" to "6273,54",
            "feePercent" to "10,7",
            "familySize" to 3,
            "incomeMinThreshold" to "-2713,00",
            "showValidTo" to true,
            "approverFirstName" to "Pirkko",
            "approverLastName" to "Päättäjä",
            "decisionMakerName" to "",
            "decisionMakerTitle" to "",
            "showTotalIncome" to true,
            "isElementaryFamily" to false
        )
        simpleVariables.forEach { (key, item) -> assertEquals(expected.getValue(key), item) }
    }

    @Test
    fun `variables are ok with relief decision`() {
        val feeDecisionPdfData = FeeDecisionPdfData(
            decision = reliefDecision,
            settings = mapOf(),
            lang = DocumentLang.fi.name
        )

        val simpleVariables = service.getFeeDecisionPdfVariables(feeDecisionPdfData).filterKeys {
            it != "parts"
        }

        val expectedSendAddress = DecisionSendAddress(
            "Kamreerintie 2",
            "02770",
            "Espoo",
            "Kamreerintie 2",
            "02770 Espoo",
            ""
        )

        val expected = mapOf(
            "approvedAt" to "15.04.2019",
            "decisionNumber" to 1010101010L,
            "decisionType" to "RELIEF_ACCEPTED",
            "isReliefDecision" to true,
            "hasPartner" to true,
            "headFullName" to "John Doe",
            "headIncomeEffect" to "INCOME",
            "headIncomeTotal" to "2141,59",
            "partnerFullName" to "Joan Doe",
            "partnerIncomeEffect" to "INCOME",
            "partnerIncomeTotal" to "4131,95",
            "sendAddress" to expectedSendAddress,
            "totalFee" to "434,00",
            "validFor" to "01.05.2019 - 31.05.2019",
            "validFrom" to "01.05.2019",
            "validTo" to "31.05.2019",
            "totalIncome" to "6273,54",
            "feePercent" to "10,7",
            "familySize" to 3,
            "incomeMinThreshold" to "-2713,00",
            "showValidTo" to true,
            "approverFirstName" to "Pirkko",
            "approverLastName" to "Päättäjä",
            "decisionMakerName" to "",
            "decisionMakerTitle" to "",
            "showTotalIncome" to true,
            "isElementaryFamily" to false
        )
        simpleVariables.forEach { (key, item) -> assertEquals(expected.getValue(key), item) }
    }

    @Test
    fun `generateFeeDecisionPdf smoke test`() {
        val feeDecisionPdfData = FeeDecisionPdfData(
            decision = normalDecision,
            settings = mapOf(),
            lang = DocumentLang.fi.name
        )
        val pdfBytes = service.generateFeeDecisionPdf(feeDecisionPdfData)

        // File("/tmp/fee_decision_test.pdf").writeBytes(pdfBytes)

        assertNotNull(pdfBytes)
    }

    @Test
    fun `generateVoucherValueDecisionPdf smoke test`() {

        val voucherValueDecisionPdfData = VoucherValueDecisionPdfData(
            decision = normalVoucherValueDecision,
            settings = mapOf(),
            lang = DocumentLang.fi
        )
        val pdfBytes = service.generateVoucherValueDecisionPdf(voucherValueDecisionPdfData)
        // File("/tmp/voucher_value_decision_test.pdf").writeBytes(pdfBytes)

        assertNotNull(pdfBytes)
    }
}
