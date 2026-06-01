package vn.delfi.xcloudwms.data.putaway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PutawayLineValidatorTest {

    private fun input(
        from: String? = "10",
        to: String? = "20",
        hasProductOrCode: Boolean = true,
        qty: Double? = 1.0,
        available: Double? = null,
    ) = PutawayLineValidator.Input(
        fromLocationId = from,
        toLocationId = to,
        hasProductOrCode = hasProductOrCode,
        quantity = qty,
        availableQty = available,
    )

    @Test
    fun `valid line passes`() {
        assertEquals(PutawayLineValidator.Result.Ok, PutawayLineValidator.validate(input()))
    }

    @Test
    fun `missing from location fails`() {
        val result = PutawayLineValidator.validate(input(from = null))
        assertTrue(result is PutawayLineValidator.Result.Invalid)
        assertEquals(PutawayLineValidator.Field.FROM_LOCATION, (result as PutawayLineValidator.Result.Invalid).field)
    }

    @Test
    fun `missing product or code fails`() {
        val result = PutawayLineValidator.validate(input(hasProductOrCode = false))
        assertEquals(
            PutawayLineValidator.Field.PRODUCT,
            (result as PutawayLineValidator.Result.Invalid).field,
        )
    }

    @Test
    fun `zero or negative qty fails`() {
        assertEquals(
            PutawayLineValidator.Field.QTY,
            (PutawayLineValidator.validate(input(qty = 0.0)) as PutawayLineValidator.Result.Invalid).field,
        )
        assertEquals(
            PutawayLineValidator.Field.QTY,
            (PutawayLineValidator.validate(input(qty = null)) as PutawayLineValidator.Result.Invalid).field,
        )
    }

    @Test
    fun `missing to location fails`() {
        val result = PutawayLineValidator.validate(input(to = null))
        assertEquals(
            PutawayLineValidator.Field.TO_LOCATION,
            (result as PutawayLineValidator.Result.Invalid).field,
        )
    }

    @Test
    fun `same from and to fails`() {
        val result = PutawayLineValidator.validate(input(from = "7", to = "7"))
        assertEquals(
            PutawayLineValidator.Field.TO_LOCATION,
            (result as PutawayLineValidator.Result.Invalid).field,
        )
    }

    @Test
    fun `qty above available fails when available known`() {
        val result = PutawayLineValidator.validate(input(qty = 5.0, available = 3.0))
        assertEquals(
            PutawayLineValidator.Field.QTY,
            (result as PutawayLineValidator.Result.Invalid).field,
        )
    }

    @Test
    fun `qty at available passes`() {
        assertEquals(
            PutawayLineValidator.Result.Ok,
            PutawayLineValidator.validate(input(qty = 3.0, available = 3.0)),
        )
    }

    @Test
    fun `formatQty trims trailing zeros`() {
        assertEquals("3", PutawayLineValidator.formatQty(3.0))
        assertEquals("2.5", PutawayLineValidator.formatQty(2.5))
    }
}
