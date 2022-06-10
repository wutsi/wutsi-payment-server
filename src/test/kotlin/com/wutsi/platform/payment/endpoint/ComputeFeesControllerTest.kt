package com.wutsi.platform.payment.endpoint

import com.wutsi.platform.payment.PaymentMethodType
import com.wutsi.platform.payment.dto.ComputeFeesRequest
import com.wutsi.platform.payment.dto.ComputeFeesResponse
import com.wutsi.platform.payment.entity.TransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ComputeFeesControllerTest : AbstractSecuredController() {
    @LocalServerPort
    val port: Int = 0

    private lateinit var url: String

    @BeforeEach
    override fun setUp() {
        super.setUp()

        url = "http://localhost:$port/v1/transactions/fees"
    }

    @Test
    fun charge() {
        // WHEN
        val request = createRequest(TransactionType.CHARGE, PaymentMethodType.MOBILE)
        val response = rest.postForEntity(url, request, ComputeFeesResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        // THEN
        val amount = 100.0
        val fee = response.body!!.fee
        assertEquals(request.currency, fee.currency)
        assertEquals(request.amount, fee.amount)
        assertEquals(false, fee.applyFeesToSender)
        assertEquals(amount, fee.fees)
        assertEquals(request.amount, fee.senderAmount)
        assertEquals(request.amount - amount, fee.recipientAmount)
    }

    @Test
    fun cashin() {
        // WHEN
        val request = createRequest(TransactionType.CASHIN, PaymentMethodType.MOBILE)
        val response = rest.postForEntity(url, request, ComputeFeesResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        // THEN
        val amount = 25.0
        val fee = response.body!!.fee
        assertEquals(request.currency, fee.currency)
        assertEquals(request.amount, fee.amount)
        assertEquals(true, fee.applyFeesToSender)
        assertEquals(amount, fee.fees)
        assertEquals(request.amount + amount, fee.senderAmount)
        assertEquals(request.amount, fee.recipientAmount)
    }

    @Test
    fun cashout() {
        // WHEN
        val request = createRequest(TransactionType.CASHOUT, PaymentMethodType.MOBILE)
        val response = rest.postForEntity(url, request, ComputeFeesResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        // THEN
        val amount = 20.0
        val fee = response.body!!.fee
        assertEquals(request.currency, fee.currency)
        assertEquals(request.amount, fee.amount)
        assertEquals(true, fee.applyFeesToSender)
        assertEquals(amount, fee.fees)
        assertEquals(request.amount + amount, fee.senderAmount)
        assertEquals(request.amount, fee.recipientAmount)
    }

    @Test
    fun transfer() {
        // WHEN
        val request = createRequest(TransactionType.TRANSFER, null)
        val response = rest.postForEntity(url, request, ComputeFeesResponse::class.java)

        // THEN
        assertEquals(200, response.statusCodeValue)

        // THEN
        val amount = 0.0
        val fee = response.body!!.fee
        assertEquals(request.currency, fee.currency)
        assertEquals(request.amount, fee.amount)
        assertEquals(false, fee.applyFeesToSender)
        assertEquals(amount, fee.fees)
        assertEquals(request.amount + amount, fee.senderAmount)
        assertEquals(request.amount, fee.recipientAmount)
    }

    private fun createRequest(transactionType: TransactionType, paymentMethodType: PaymentMethodType?) =
        ComputeFeesRequest(
            transactionType = transactionType.name,
            paymentMethodType = paymentMethodType?.name,
            amount = 1000.0,
            currency = "XAF"
        )
}
