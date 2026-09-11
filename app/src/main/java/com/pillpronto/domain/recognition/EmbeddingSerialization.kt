package com.pillpronto.domain.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Serializare `FloatArray` <-> `ByteArray` (Faza 4b — persistarea embeddings-urilor de
 * recunoaștere într-o coloană BLOB Room, care mapează nativ `ByteArray` fără TypeConverter).
 * `java.nio.ByteBuffer` e parte din JDK, NU din Android — respectă regula de puritate a
 * stratului `domain` (fără dependențe Android), la fel ca restul din `domain/recognition`. Ordinea
 * de octeți (`LITTLE_ENDIAN`) contează doar pt. simetrie scriere/citire, nu pt. compatibilitate
 * externă — embeddings-urile nu ies niciodată din acest proces. */

fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    for (value in this) buffer.putFloat(value)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val floatCount = size / Float.SIZE_BYTES
    return FloatArray(floatCount) { buffer.float }
}
