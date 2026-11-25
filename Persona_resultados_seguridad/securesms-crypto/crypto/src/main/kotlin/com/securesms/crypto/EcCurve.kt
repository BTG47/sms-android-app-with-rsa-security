package com.securesms.crypto

import java.math.BigInteger
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve

/**
 * Domain parameters of the NIST prime curves (FIPS 186-5 / SEC 2), used to validate keys and to
 * compress / decompress points. A unit test checks they equal the parameters produced by the JCA.
 */
internal class EcCurve private constructor(
    val jcaName: String,
    val p: BigInteger,
    val a: BigInteger,
    val b: BigInteger,
    val gx: BigInteger,
    val gy: BigInteger,
    val n: BigInteger,
) {
    val coordinateLength: Int = (p.bitLength() + 7) / 8

    val spec: ECParameterSpec =
        ECParameterSpec(EllipticCurve(ECFieldFp(p), a, b), ECPoint(gx, gy), n, 1)

    /** True when [params] describe this curve (provider-independent comparison). */
    fun matches(params: ECParameterSpec): Boolean {
        val field = params.curve.field as? ECFieldFp ?: return false
        return field.p == p && params.curve.a == a && params.curve.b == b &&
            params.generator.affineX == gx && params.generator.affineY == gy &&
            params.order == n && params.cofactor == 1
    }

    fun isOnCurve(point: ECPoint): Boolean {
        if (point == ECPoint.POINT_INFINITY) return false
        val x = point.affineX
        val y = point.affineY
        if (x.signum() < 0 || x >= p || y.signum() < 0 || y >= p) return false
        val lhs = y.multiply(y).mod(p)
        val rhs = x.pow(3).add(a.multiply(x)).add(b).mod(p)
        return lhs == rhs
    }

    /** SEC 1 compressed encoding: 0x02/0x03 (parity of y) followed by x. */
    fun compress(point: ECPoint): ByteArray {
        val out = ByteArray(1 + coordinateLength)
        out[0] = if (point.affineY.testBit(0)) 0x03 else 0x02
        toFixed(point.affineX, coordinateLength).copyInto(out, 1)
        return out
    }

    /** SEC 1 uncompressed encoding: 0x04 || x || y (used for fingerprints). */
    fun uncompressed(point: ECPoint): ByteArray =
        byteArrayOf(0x04) + toFixed(point.affineX, coordinateLength) + toFixed(point.affineY, coordinateLength)

    /** Inverse of [compress]; rejects anything that is not a valid point on this curve. */
    fun decompress(encoded: ByteArray): ECPoint {
        if (encoded.size != 1 + coordinateLength || (encoded[0] != 0x02.toByte() && encoded[0] != 0x03.toByte())) {
            fail(ErrorCode.INVALID_EPHEMERAL_KEY, "Ephemeral key must be a ${1 + coordinateLength}-byte compressed point")
        }
        val x = BigInteger(1, encoded.copyOfRange(1, encoded.size))
        if (x >= p) fail(ErrorCode.INVALID_EPHEMERAL_KEY, "Ephemeral x coordinate out of range")
        val rhs = x.pow(3).add(a.multiply(x)).add(b).mod(p)
        // p = 3 (mod 4) for P-256 and P-384, so sqrt(rhs) = rhs^((p+1)/4) mod p.
        var y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        if (y.multiply(y).mod(p) != rhs) fail(ErrorCode.INVALID_EPHEMERAL_KEY, "Ephemeral point is not on the curve")
        val wantOdd = encoded[0] == 0x03.toByte()
        if (y.testBit(0) != wantOdd) y = p.subtract(y)
        val point = ECPoint(x, y)
        if (!isOnCurve(point)) fail(ErrorCode.INVALID_EPHEMERAL_KEY, "Ephemeral point is not on the curve")
        return point
    }

    companion object {
        private fun h(s: String) = BigInteger(s, 16)

        val P256 = EcCurve(
            "secp256r1",
            p = h("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF"),
            a = h("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC"),
            b = h("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B"),
            gx = h("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296"),
            gy = h("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5"),
            n = h("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551"),
        )

        val P384 = EcCurve(
            "secp384r1",
            p = h("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF"),
            a = h("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC"),
            b = h("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF"),
            gx = h("AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E082542A385502F25DBF55296C3A545E3872760AB7"),
            gy = h("3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F"),
            n = h("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973"),
        )

        /** Big-endian, unsigned, left-padded to [length] bytes. */
        fun toFixed(value: BigInteger, length: Int): ByteArray {
            val raw = value.toByteArray()
            return when {
                raw.size == length -> raw
                raw.size == length + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
                raw.size < length -> ByteArray(length - raw.size) + raw
                else -> throw IllegalArgumentException("Value does not fit in $length bytes")
            }
        }
    }
}
