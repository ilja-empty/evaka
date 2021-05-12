// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import fi.espoo.evaka.invoicing.domain.FeeDecision2
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.generic.GenericType
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.mapper.SingleColumnMapper
import org.jdbi.v3.core.qualifier.QualifiedType
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.core.statement.SqlStatement
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.jackson2.Jackson2Config
import org.jdbi.v3.jackson2.Jackson2Plugin
import org.jdbi.v3.json.Json
import org.jdbi.v3.postgres.PostgresPlugin
import java.sql.ResultSet
import java.util.UUID

private inline fun <reified T> Jdbi.register(columnMapper: ColumnMapper<T>) {
    registerColumnMapper(T::class.java, columnMapper)
    // Support mapping a single column result.
    // We need an explicit row mapper for T, or JDBI KotlinMapper will try to map it field-by-field, even in the single column case
    registerRowMapper(T::class.java, SingleColumnMapper(columnMapper))
}

fun configureJdbi(jdbi: Jdbi): Jdbi {
    val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(Jdk8Module())
        .registerModule(ParameterNamesModule())
        .registerModule(KotlinModule())
    jdbi.installPlugin(KotlinPlugin())
        .installPlugin(PostgresPlugin())
        .installPlugin(Jackson2Plugin())
    jdbi.getConfig(Jackson2Config::class.java).mapper = objectMapper
    jdbi.registerArgument(finiteDateRangeArgumentFactory)
    jdbi.registerArgument(dateRangeArgumentFactory)
    jdbi.registerArgument(coordinateArgumentFactory)
    jdbi.registerArgument(identityArgumentFactory)
    jdbi.registerArgument(externalIdArgumentFactory)
    jdbi.registerArgument(helsinkiDateTimeArgumentFactory)
    jdbi.register(finiteDateRangeColumnMapper)
    jdbi.register(dateRangeColumnMapper)
    jdbi.register(coordinateColumnMapper)
    jdbi.register(externalIdColumnMapper)
    jdbi.register(helsinkiDateTimeColumnMapper)
    jdbi.registerArrayType(UUID::class.java, "uuid")
    jdbi.registerRowMapper(FeeDecision2::class.java, feeDecisionRowMapper(objectMapper))
    return jdbi
}

/**
 * Binds a nullable argument to an SQL statement.
 *
 * SqlStatement.`bind` can't handle null values, because it figures out the type from the passed value, and if the
 * value is null, there is no type information available. This function uses Kotlin reified types to figure out the type
 * at compile-time.
 */
inline fun <reified T : Any, This : SqlStatement<This>> SqlStatement<This>.bindNullable(name: String, value: T?): This =
    this.bindByType(name, value, QualifiedType.of(T::class.java))

inline fun <reified T : Any, This : SqlStatement<This>> SqlStatement<This>.bindNullable(
    name: String,
    value: Collection<T>?
): This =
    this.bindNullable(name, value?.toTypedArray())

/**
 * Maps a result set column to a value.
 *
 * This function is often better than rs.getXXX() functions, because configured Jdbi column mappers are
 * used when appropriate and it's not restricted to types supported by the underlying ResultSet.
 */
inline fun <reified T : Any> StatementContext.mapColumn(rs: ResultSet, name: String): T =
    mapNullableColumn(rs, name) ?: throw IllegalStateException("Non-nullable column $name was null")

/**
 * Maps a result set column to a nullable value.
 *
 * This function is often better than rs.getXXX() functions, because configured Jdbi column mappers are
 * used when appropriate and it's not restricted to types supported by the underlying ResultSet.
 */
inline fun <reified T : Any> StatementContext.mapNullableColumn(rs: ResultSet, name: String): T? =
    findColumnMapperFor(T::class.java).orElseThrow {
        throw IllegalStateException("No column mapper found for type ${T::class}")
    }.map(rs, name, this)

/**
 * Maps a row column to a value.
 *
 * This function works with Kotlin better than row.getColumn().
 */
inline fun <reified T> RowView.mapColumn(name: String, type: QualifiedType<T> = QualifiedType.of(T::class.java)): T {
    val value = getColumn(name, type)
    if (null !is T && value == null) {
        throw throw IllegalStateException("Non-nullable column $name was null")
    }
    return value
}

/**
 * Maps a row json column to a value.
 */
inline fun <reified T : Any?> RowView.mapJsonColumn(name: String): T =
    mapColumn(name, QualifiedType.of(object : GenericType<T>() {}).with(Json::class.java))
