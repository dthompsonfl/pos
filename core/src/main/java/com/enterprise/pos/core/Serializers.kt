package com.enterprise.pos.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

/** Serializes Money as its Long minor-units representation. Compact and exact. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeLong(value.minorUnits)
    }

    override fun deserialize(decoder: Decoder): Money = Money.ofMinor(decoder.decodeLong())
}

/** Serializes Percent as its basis-points Int. */
object PercentSerializer : KSerializer<Percent> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Percent", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Percent) {
        encoder.encodeInt(value.basisPoints)
    }

    override fun deserialize(decoder: Decoder): Percent = Percent.ofBasisPoints(decoder.decodeInt())
}

/** Serializes Quantity as a String (BigDecimal toPlainString). */
object QuantitySerializer : KSerializer<Quantity> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Quantity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Quantity) {
        encoder.encodeString(value.value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): Quantity = Quantity.parse(decoder.decodeString())
}
