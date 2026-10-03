package com.example.inventorytracker.data.remote.sync

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/** Reads legacy desktop SQLite values (0/1) as well as the current JSON true/false values. */
class LegacyBooleanJsonAdapter : JsonAdapter<Boolean>() {
    override fun fromJson(reader: JsonReader): Boolean? = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> reader.nextBoolean()
        JsonReader.Token.NUMBER -> reader.nextDouble() != 0.0
        JsonReader.Token.STRING -> when (reader.nextString().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> throw JsonDataException("Expected Boolean or 0/1 at ${reader.path}")
        }
        JsonReader.Token.NULL -> reader.nextNull()
        else -> throw JsonDataException("Expected Boolean or 0/1 at ${reader.path}")
    }

    override fun toJson(writer: JsonWriter, value: Boolean?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
