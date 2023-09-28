// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package evaka.codegen.apitypes

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import evaka.codegen.fileHeader
import fi.espoo.evaka.ConstList
import fi.espoo.evaka.ExcludeCodeGen
import fi.espoo.evaka.ForceCodeGenType
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

fun generateApiTypes(target: Path) {
    target.toFile().deleteRecursively()
    target.createDirectory()

    getApiTypesInTypeScript(target).entries.forEach { (path, content) -> path.writeText(content) }
}

fun getApiTypesInTypeScript(root: Path): Map<Path, String> {
    return analyzeClasses().entries.associate { (mainPackage, classes) ->
        val conflicts = classes.groupBy { it.name }.filter { it.value.size > 1 }
        conflicts.forEach { (name, conflictingClasses) ->
            logger.error(
                "Multiple Kotlin classes map to $name: ${conflictingClasses.map { it.fullName }}"
            )
        }
        if (conflicts.isNotEmpty()) {
            error("${conflicts.size} classes are generated by more than one Kotlin class")
        }
        val path = root / "$mainPackage.ts"
        val content =
            """$fileHeader
${getImports(classes).sorted().joinToString("\n")}

${classes.sortedBy { it.name }.joinToString("\n\n") { it.toTs() }}
"""

        path to content
    }
}

private fun getImports(classes: List<AnalyzedClass>): List<String> {
    val classesToImport =
        classes
            .flatMap { it.declarableTypes() }
            .map { it.qualifiedName!! }
            .filter { classes.none { c -> c.fullName == it } }
            .toSet()

    return classesToImport
        .mapNotNull {
            if (tsMapping.containsKey(it)) {
                tsMapping[it]?.import
            } else {
                "import { ${it.substringAfterLast('.')} } from './${getBasePackage(it)}'"
            }
        }
        .distinct()
}

private fun analyzeClasses(): Map<String, List<AnalyzedClass>> {
    val knownClasses = tsMapping.keys.toMutableSet()
    val analyzedClasses = mutableListOf<AnalyzedClass>()

    val waiting = ArrayDeque<KClass<*>>()
    waiting.addAll(getApiClasses(basePackage).filter { !knownClasses.contains(it.qualifiedName) })

    while (waiting.isNotEmpty()) {
        val analyzed = analyzeClass(waiting.removeFirst()) ?: continue
        analyzedClasses.add(analyzed)
        knownClasses.add(analyzed.fullName)
        analyzed.declarableTypes().forEach { type ->
            if (!knownClasses.contains(type.qualifiedName) && waiting.none { it == type }) {
                waiting.addLast(type)
            }
        }
    }

    return analyzedClasses.groupBy { getBasePackage(it.fullName) }
}

private fun analyzeClass(clazz: KClass<*>): AnalyzedClass? {
    if (
        (clazz.allSupertypes.map { it.jvmErasure } + clazz).any { cls ->
            cls.annotations.any { it.annotationClass == ExcludeCodeGen::class }
        }
    ) {
        return null
    }

    if (
        clazz.qualifiedName?.startsWith("kotlin.") == true ||
            clazz.qualifiedName?.startsWith("java.") == true
    ) {
        error("Kotlin/Java class ${clazz.qualifiedName} not handled, add to tsMapping")
    }

    return when {
        clazz.java.enumConstants?.isNotEmpty() == true ->
            AnalyzedClass.EnumClass(
                name = clazz.qualifiedName ?: error("no class name"),
                values = clazz.java.enumConstants.map { it.toString() },
                constList =
                    clazz.java.annotations
                        .find { it.annotationClass == ConstList::class }
                        ?.let { it as? ConstList }
                        ?.name
            )
        clazz.isData ->
            AnalyzedClass.DataClass(
                name = clazz.qualifiedName ?: error("no class name"),
                properties = clazz.declaredMemberProperties.map { analyzeMemberProperty(it) }
            )
        clazz.isSealed -> {
            clazz.annotations
                .find { it.annotationClass == JsonTypeInfo::class }
                ?.let { it as JsonTypeInfo }
                ?.let { jsonTypeInfo ->
                    when (jsonTypeInfo.use) {
                        JsonTypeInfo.Id.NAME -> {
                            // Discriminated union
                            AnalyzedClass.SealedClass(
                                name = clazz.qualifiedName ?: error("no class name"),
                                nestedClasses =
                                    clazz.nestedClasses
                                        .filterNot { it.isCompanion }
                                        .map {
                                            analyzeDiscriminatedUnionMember(
                                                jsonTypeInfo.property,
                                                it
                                            )
                                        },
                                ownProperties =
                                    clazz.declaredMemberProperties.map { analyzeMemberProperty(it) }
                            )
                        }
                        JsonTypeInfo.Id.DEDUCTION ->
                            // Just union
                            AnalyzedClass.SealedClass(
                                name = clazz.qualifiedName ?: error("no class name"),
                                nestedClasses =
                                    clazz.nestedClasses
                                        .filterNot { it.isCompanion }
                                        .mapNotNull { analyzeClass(it) },
                                ownProperties =
                                    clazz.declaredMemberProperties.map { analyzeMemberProperty(it) }
                            )
                        else -> null
                    }
                }
        }
        else -> error("unhandled case: $clazz")
    }
}

private fun analyzeMemberProperty(prop: KProperty1<out Any, *>): AnalyzedProperty {
    val forcedType =
        prop.findAnnotation() ?: prop.javaField?.getAnnotation(ForceCodeGenType::class.java)
    val type =
        forcedType?.type?.createType(nullable = prop.returnType.isMarkedNullable) ?: prop.returnType
    return AnalyzedProperty(prop.name, analyzeType(type))
}

private fun analyzeDiscriminatedUnionMember(discriminant: String, clazz: KClass<*>): AnalyzedClass {
    val discriminantValue =
        clazz.annotations
            .find { it.annotationClass == JsonTypeName::class }
            ?.let { annotation -> (annotation as JsonTypeName).value }
            ?: error("Nested class ${clazz.qualifiedName} is missing JsonTypeName annotation")

    val discriminantProperty = AnalyzedProperty(discriminant, TsStringLiteral(discriminantValue))
    return when {
        clazz.isData ->
            AnalyzedClass.DataClass(
                name = clazz.qualifiedName ?: error("no class name"),
                properties =
                    listOf(discriminantProperty) +
                        clazz.declaredMemberProperties.map { analyzeMemberProperty(it) }
            )
        clazz.objectInstance != null ->
            AnalyzedClass.DataClass(
                name = clazz.qualifiedName ?: error("no class name"),
                properties = listOf(discriminantProperty)
            )
        else -> error("unhandled case: $clazz")
    }
}

private sealed class AnalyzedClass(val fullName: String) {
    val name: String = fullName.substringAfterLast('.')

    abstract fun declarableTypes(): List<KClass<*>>

    abstract fun toTs(): String

    class DataClass(name: String, val properties: List<AnalyzedProperty>) : AnalyzedClass(name) {
        override fun declarableTypes(): List<KClass<*>> {
            return properties.flatMap { it.type.declarableTypes }
        }

        override fun toTs(): String {
            return """/**
* Generated from $fullName
*/
export interface $name {
${properties.joinToString("\n") { "  " + it.toTs() }}
}"""
        }
    }

    class EnumClass(name: String, val values: List<String>, val constList: String?) :
        AnalyzedClass(name) {
        override fun declarableTypes(): List<KClass<*>> = emptyList()

        override fun toTs(): String {
            val doc = """/**
* Generated from $fullName
*/"""
            if (constList != null) {
                return doc +
                    """
export const $constList = [
${values.joinToString(",\n") { "  '$it'" }}
] as const

export type $name = typeof $constList[number]"""
            }

            return doc + """
export type $name =
${values.joinToString("\n") { "  | '$it'" }}"""
        }
    }

    class SealedClass(
        name: String,
        val nestedClasses: List<AnalyzedClass>,
        val ownProperties: List<AnalyzedProperty>
    ) : AnalyzedClass(name) {
        override fun declarableTypes(): List<KClass<*>> {
            return nestedClasses.flatMap { it.declarableTypes() } +
                ownProperties.flatMap { it.type.declarableTypes }
        }

        override fun toTs(): String {
            return """export namespace $name {
${nestedClasses.joinToString("\n\n") { it.toTs() }.prependIndent("  ")}
}

/**
* Generated from $fullName
*/
export type $name = ${nestedClasses.joinToString(" | ") { "$name.${it.name}" }}
"""
        }
    }
}

data class AnalyzedProperty(val name: String, val type: AnalyzedType) {
    fun toTs() = "$name: ${type.toTs()}"
}

fun analyzeType(type: KType): AnalyzedType =
    when {
        isMap(type) -> TsMap(type)
        isCollection(type) -> TsArray(type)
        else -> TsPlain(type)
    }

sealed interface AnalyzedType {
    val declarableTypes: List<KClass<*>>

    fun toTs(): String
}

data class TsPlain(val type: KType) : AnalyzedType {
    override val declarableTypes = listOf(type.jvmErasure)

    override fun toTs() = toTs(type)
}

data class TsStringLiteral(val value: String) : AnalyzedType {
    override val declarableTypes = emptyList<KClass<*>>()

    override fun toTs() = "'$value'"
}

data class TsArray(val type: KType) : AnalyzedType {
    private val typeParameter = unwrapCollection(type)
    override val declarableTypes = listOf(typeParameter.jvmErasure)

    override fun toTs() =
        toTs(typeParameter)
            .let { if (typeParameter.isMarkedNullable) "($it)" else it }
            .let { "$it[]" }
            .let { if (type.isMarkedNullable) "$it | null" else it }

    private fun unwrapCollection(type: KType): KType {
        return when (type) {
            IntArray::class -> Int::class.createType()
            DoubleArray::class -> Double::class.createType()
            BooleanArray::class -> Boolean::class.createType()
            else -> type.arguments.first().type!!
        }
    }
}

data class TsMap(val type: KType) : AnalyzedType {
    private val keyType: KType = run {
        val keyType = type.arguments[0].type!!
        val isEnumType = keyType.jvmErasure.java.enumConstants?.isNotEmpty() ?: false
        if (validMapKeyTypes.none { it == keyType.jvmErasure } && !isEnumType) {
            // Key is not an enum or an allowed type
            error("Unsupported Map key type $keyType")
        }

        if (isEnumType) keyType else String::class.createType()
    }
    private val valueType = analyzeType(type.arguments[1].type!!)

    override val declarableTypes = valueType.declarableTypes + keyType.jvmErasure

    override fun toTs(): String =
        "Record<${toTs(keyType)}, ${valueType.toTs()}>"
            .let { if (type.isMarkedNullable) "$it | null" else it }
}

private fun toTs(type: KType): String {
    val className = tsMapping[type.jvmErasure.qualifiedName]?.type ?: type.jvmErasure.simpleName!!
    return if (type.isMarkedNullable) "$className | null" else className
}

private fun isMap(type: KType): Boolean {
    return type.jvmErasure.isSubclassOf(Map::class)
}

private fun isCollection(type: KType): Boolean {
    return kotlinCollectionClasses.any { type.jvmErasure.isSubclassOf(it) }
}

private fun getBasePackage(fullyQualifiedName: String): String {
    val pkg = fullyQualifiedName.substringBeforeLast('.')
    val relativePackage =
        when {
            pkg == basePackage -> return "base"
            pkg.startsWith("$basePackage.") -> pkg.substring(basePackage.length + 1)
            else -> error("class not under base package")
        }
    return relativePackage.substringBefore('.')
}
