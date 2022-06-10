package com.wutsi.platform.payment.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.platform.payment.PaymentException
import com.wutsi.platform.payment.core.Money
import com.wutsi.platform.payment.core.Status
import com.wutsi.platform.payment.delegate.CreateCashinDelegate
import com.wutsi.platform.payment.delegate.CreateCashoutDelegate
import com.wutsi.platform.payment.delegate.CreateChargeDelegate
import com.wutsi.platform.payment.endpoint.AbstractSecuredController
import com.wutsi.platform.payment.model.CreatePaymentResponse
import com.wutsi.platform.payment.model.CreateTransferResponse
import com.wutsi.platform.payment.model.GetPaymentResponse
import com.wutsi.platform.payment.model.GetTransferResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(value = ["/db/clean.sql", "/db/TransactionEventHandler.sql"])
internal class TransactionEventHandlerTest : AbstractSecuredController() {
    @MockBean
    private lateinit var cashinDelegate: CreateCashinDelegate

    @MockBean
    private lateinit var cashoutDelegate: CreateCashoutDelegate

    @MockBean
    private lateinit var chargeDelegate: CreateChargeDelegate

    @Autowired
    private lateinit var handler: TransactionEventHandler

    @Test
    fun syncSuccessfulCashin() {
        // GIVEN
        val response = GetPaymentResponse(
            status = Status.SUCCESSFUL,
            financialTransactionId = "xxxx",
            fees = Money(100.0, "XAF")
        )
        doReturn(response).whenever(gateway).getPayment(any())

        // WHEN
        handler.onSync("100")

        // THEN
        verify(cashinDelegate).onSuccess(
            any(),
            eq(
                CreatePaymentResponse(
                    transactionId = "gw-100",
                    financialTransactionId = response.financialTransactionId,
                    status = response.status,
                    fees = Money(100.0, "XAF")
                )
            ),
            eq(tenant)
        )
    }

    @Test
    fun syncFailedCashin() {
        // GIVEN
        val ex = PaymentException()
        doThrow(ex).whenever(gateway).getPayment(any())

        // WHEN
        handler.onSync("100")

        // THEN
        verify(cashinDelegate).onError(any(), eq(ex), any())
    }

    @Test
    fun syncSuccessfulCashout() {
        // GIVEN
        val response = GetTransferResponse(
            status = Status.SUCCESSFUL,
            financialTransactionId = "xxxx",
            fees = Money(100.0, "XAF")
        )
        doReturn(response).whenever(gateway).getTransfer(any())

        // WHEN
        handler.onSync("200")

        // THEN
        verify(cashoutDelegate).onSuccess(
            any(),
            eq(
                CreateTransferResponse(
                    transactionId = "gw-200",
                    financialTransactionId = response.financialTransactionId,
                    status = response.status,
                    fees = Money(100.0, "XAF")
                )
            )
        )
    }

    @Test
    fun syncFailedCashout() {
        // GIVEN
        val ex = PaymentException()
        doThrow(ex).whenever(gateway).getTransfer(any())

        // WHEN
        handler.onSync("200")

        // THEN
        verify(cashoutDelegate).onError(any(), eq(ex), any())
    }

    @Test
    fun syncSuccessfulCharge() {
        // GIVEN
        val response = GetPaymentResponse(
            status = Status.SUCCESSFUL,
            financialTransactionId = "xxxx",
            fees = Money(100.0, "XAF")
        )
        doReturn(response).whenever(gateway).getPayment(any())

        // WHEN
        handler.onSync("400")

        // THEN
        verify(chargeDelegate).onSuccess(
            any(),
            eq(
                CreatePaymentResponse(
                    transactionId = "gw-400",
                    financialTransactionId = response.financialTransactionId,
                    status = response.status,
                    fees = Money(100.0, "XAF")
                )
            ),
            eq(tenant)
        )
    }

    @Test
    fun syncFailedCharge() {
        // GIVEN
        val ex = PaymentException()
        doThrow(ex).whenever(gateway).getPayment(any())

        // WHEN
        handler.onSync("400")

        // THEN
        verify(chargeDelegate).onError(any(), eq(ex), any())
    }
}
