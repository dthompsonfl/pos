package com.enterprise.pos.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeLong(value.minorUnits)
    }

    override fun deserialize(decoder: Decoder): Money {
        return Money.ofMinor(decoder.decodeLong())
    }
}

object PercentSerializer : KSerializer<Percent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Percent", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Percent) {
        encoder.encodeInt(value.basisPoints)
    }

    override fun deserialize(decoder: Decoder): Percent {
        return Percent.ofBasisPoints(decoder.decodeInt())
    }
}

object QuantitySerializer : KSerializer<Quantity> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Quantity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Quantity) {
        encoder.encodeString(value.value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): Quantity {
        return Quantity.of(BigDecimal(decoder.decodeString()))
    }
}
