package es.wokis.voice

import es.wokis.commands.voicewalkthrough.JdaVoiceWalkthroughCommand
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.AnnotatedType
import java.lang.reflect.GenericArrayType
import java.lang.reflect.GenericDeclaration
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Guards the Phase 2C neutral boundary: no gateway, DAVE or Discord type may appear in the
 * public surface of the new voice/audio/player-message contracts, values or command.
 *
 * The inspection is recursive. Generic arguments, parameterized owner types, generic array
 * components, wildcard upper/lower bounds and type-variable bounds are all walked, so a
 * signature such as `List<net.dv8tion.jda.api.JDA>` or `Array<dev.kord.core.Kord>` cannot
 * hide behind a neutral raw type. Arrays, supertypes, interfaces, constructors, fields and
 * methods are inspected, including generic exception types and method/constructor type
 * parameters.
 */
class VoiceBoundaryReflectionTest {

    private val neutralTypes = listOf(
        AudioFrame::class.java,
        AudioFrameFormat::class.java,
        AudioFrameSource::class.java,
        VoiceSessionPort::class.java,
        VoicePlaybackResult::class.java,
        VoicePlaybackResult.Completed::class.java,
        VoicePlaybackResult.Rejected::class.java,
        VoicePlaybackResult.Failed::class.java,
        VoiceRequestRejection::class.java,
        VoiceFailureReason::class.java,
        PlayerMessageId::class.java,
        PlayerMessagePort::class.java,
        JdaVoiceWalkthroughCommand::class.java,
    )

    private val forbiddenPrefixes = listOf(
        "net.dv8tion.",
        "dev.kord.",
        "moe.kyokobot.libdave.",
    )

    @Test
    fun `Given the new voice and player contracts When inspected Then expose no gateway or DAVE types`() {
        // When
        val offenders = neutralTypes.flatMap { type -> gatewayReferences(type) }

        // Then
        assertTrue(offenders.isEmpty(), "Gateway or DAVE types leaked into :core: $offenders")
    }

    @Test
    fun `Given a gateway type as a generic argument When inspected Then the signature is rejected`() {
        // Given: the neutral raw type List hides a JDA argument, mirroring List<JDA>.
        val signature = SyntheticParameterizedType(
            raw = List::class.java,
            arguments = listOf(NamedType("net.dv8tion.jda.api.JDA")),
        )

        // When
        val leaks = forbiddenLeaks(signature, "snapshot")

        // Then
        assertRejected(leaks, "net.dv8tion.jda.api.JDA")
    }

    @Test
    fun `Given a gateway type as an array component When inspected Then the signature is rejected`() {
        // Given: the neutral generic array wrapper hides Array<dev.kord.core.Kord>.
        val signature = SyntheticGenericArrayType(NamedType("dev.kord.core.Kord"))

        // When
        val leaks = forbiddenLeaks(signature, "frames")

        // Then
        assertRejected(leaks, "dev.kord.core.Kord")
        assertTrue(leaks.any { it.contains("[]") }, "the array location must be reported: $leaks")
    }

    @Test
    fun `Given a gateway type as a wildcard upper bound When inspected Then the signature is rejected`() {
        // Given: a ? extends JDA bound.
        val signature = SyntheticWildcardType(
            upperBounds = listOf(NamedType("net.dv8tion.jda.api.JDA")),
            lowerBounds = emptyList(),
        )

        // When
        val leaks = forbiddenLeaks(signature, "list")

        // Then
        assertRejected(leaks, "net.dv8tion.jda.api.JDA")
    }

    @Test
    fun `Given a gateway type as a wildcard lower bound When inspected Then the signature is rejected`() {
        // Given: a ? super Kord bound.
        val signature = SyntheticWildcardType(
            upperBounds = listOf(NamedType("java.lang.Object")),
            lowerBounds = listOf(NamedType("dev.kord.core.Kord")),
        )

        // When
        val leaks = forbiddenLeaks(signature, "list")

        // Then
        assertRejected(leaks, "dev.kord.core.Kord")
    }

    @Test
    fun `Given a gateway type as a type variable bound When inspected Then the signature is rejected`() {
        // Given: <T extends JDA>.
        val signature = SyntheticTypeVariable(
            variableName = "T",
            declaration = VoiceBoundaryReflectionTest::class.java,
            bounds = listOf(NamedType("net.dv8tion.jda.api.JDA")),
        )

        // When
        val leaks = forbiddenLeaks(signature, "parameter")

        // Then
        assertRejected(leaks, "net.dv8tion.jda.api.JDA")
    }

    @Test
    fun `Given a gateway type as a parameterized owner When inspected Then the signature is rejected`() {
        // Given: a nested type whose owner type is a gateway class.
        val signature = SyntheticParameterizedType(
            raw = NamedType("java.util.Map\$Entry"),
            arguments = listOf(NamedType("java.lang.String"), NamedType("java.lang.String")),
            owner = NamedType("net.dv8tion.jda.api.JDA"),
        )

        // When
        val leaks = forbiddenLeaks(signature, "entry")

        // Then
        assertRejected(leaks, "net.dv8tion.jda.api.JDA")
    }

    @Test
    fun `Given a gateway type as a forbidden raw type When inspected Then the signature is rejected`() {
        // Given: the raw type itself belongs to a forbidden package.
        val signature = SyntheticParameterizedType(
            raw = NamedType("dev.kord.core.Kord"),
            arguments = emptyList(),
        )

        // When
        val leaks = forbiddenLeaks(signature, "raw")

        // Then
        assertRejected(leaks, "dev.kord.core.Kord")
    }

    @Test
    fun `Given a deeply nested gateway type When inspected Then the signature is rejected`() {
        // Given: List<List<Array<JDA>>> with a neutral wrapper at every level.
        val signature = SyntheticParameterizedType(
            raw = List::class.java,
            arguments = listOf(
                SyntheticParameterizedType(
                    raw = List::class.java,
                    arguments = listOf(
                        SyntheticGenericArrayType(NamedType("net.dv8tion.jda.api.JDA")),
                    ),
                ),
            ),
        )

        // When
        val leaks = forbiddenLeaks(signature, "nested")

        // Then
        assertRejected(leaks, "net.dv8tion.jda.api.JDA")
    }

    @Test
    fun `Given a neutral nested signature When inspected Then nothing is rejected`() {
        // Given: the same shape as the rejection tests but with only neutral types.
        val signature = SyntheticParameterizedType(
            raw = List::class.java,
            arguments = listOf(
                SyntheticGenericArrayType(PlayerMessageId::class.java),
                SyntheticWildcardType(
                    upperBounds = listOf(PlayerMessageId::class.java),
                    lowerBounds = emptyList(),
                ),
                SyntheticTypeVariable(
                    variableName = "T",
                    declaration = VoiceBoundaryReflectionTest::class.java,
                    bounds = listOf(PlayerMessageId::class.java),
                ),
            ),
        )

        // When
        val nestedLeaks = forbiddenLeaks(signature, "neutral")
        val arrayLeaks = forbiddenLeaks(Array<PlayerMessageId>::class.java, "frames")
        val classLeaks = forbiddenLeaks(PlayerMessageId::class.java, "id")

        // Then
        assertTrue(nestedLeaks.isEmpty(), "a neutral nested signature was rejected: $nestedLeaks")
        assertTrue(arrayLeaks.isEmpty(), "a neutral array signature was rejected: $arrayLeaks")
        assertTrue(classLeaks.isEmpty(), "a neutral class signature was rejected: $classLeaks")
    }

    @Test
    @Timeout(5)
    fun `Given a self-referential type variable When inspected Then the walk terminates cleanly`() {
        // Given: <T extends T> makes a bound graph that would recurse forever without a guard.
        // When
        val leaks = forbiddenLeaks(CyclicTypeVariable, "cyclic")

        // Then
        assertTrue(leaks.isEmpty(), "a cyclic neutral bound must not produce leaks: $leaks")
    }

    private fun assertRejected(leaks: List<String>, forbiddenTypeName: String) {
        assertTrue(leaks.isNotEmpty(), "expected the signature to be rejected, but nothing leaked")
        assertTrue(
            leaks.any { it.contains(forbiddenTypeName) },
            "expected a leak naming $forbiddenTypeName, got $leaks",
        )
    }

    /**
     * Inspects the whole declared surface of [type]: the type itself, its type parameters,
     * generic supertypes, interfaces, fields, methods and constructors.
     */
    private fun gatewayReferences(type: Class<*>): List<String> {
        val origin = type.simpleName.ifBlank { type.name }
        val references = mutableListOf<String>()

        collectForbidden(type, origin, references, newVisitingSet())
        type.typeParameters.forEach { parameter ->
            collectForbidden(
                parameter,
                "$origin type parameter ${parameter.name}",
                references,
                newVisitingSet(),
            )
        }
        type.genericSuperclass?.let { superType ->
            collectForbidden(superType, "$origin extends", references, newVisitingSet())
        }
        type.genericInterfaces.forEach { implemented ->
            collectForbidden(implemented, "$origin implements", references, newVisitingSet())
        }

        type.declaredFields.forEach { field ->
            collectForbidden(field.genericType, "$origin.${field.name}", references, newVisitingSet())
        }

        type.declaredMethods.forEach { method ->
            val signature = "$origin.${method.name}"
            collectForbidden(method.genericReturnType, "$signature return", references, newVisitingSet())
            method.genericParameterTypes.forEachIndexed { index, parameter ->
                collectForbidden(parameter, "$signature parameter[$index]", references, newVisitingSet())
            }
            method.genericExceptionTypes.forEachIndexed { index, thrown ->
                collectForbidden(thrown, "$signature throws[$index]", references, newVisitingSet())
            }
            method.typeParameters.forEach { parameter ->
                collectForbidden(
                    parameter,
                    "$signature type parameter ${parameter.name}",
                    references,
                    newVisitingSet(),
                )
            }
        }

        type.declaredConstructors.forEach { constructor ->
            val signature = "$origin.<init>"
            constructor.genericParameterTypes.forEachIndexed { index, parameter ->
                collectForbidden(parameter, "$signature parameter[$index]", references, newVisitingSet())
            }
            constructor.genericExceptionTypes.forEachIndexed { index, thrown ->
                collectForbidden(thrown, "$signature throws[$index]", references, newVisitingSet())
            }
            constructor.typeParameters.forEach { parameter ->
                collectForbidden(
                    parameter,
                    "$signature type parameter ${parameter.name}",
                    references,
                    newVisitingSet(),
                )
            }
        }

        return references
    }

    private fun forbiddenLeaks(type: Type, location: String): List<String> {
        val leaks = mutableListOf<String>()
        collectForbidden(type, location, leaks, newVisitingSet())
        return leaks
    }

    /**
     * Walks one [Type] node, recording every forbidden class name it can reach.
     *
     * The [visiting] set is identity-based and only tracks the current recursion stack, so a
     * type reachable through two different paths is still inspected twice while cyclic
     * bounds such as `<T extends Comparable<T>>` terminate.
     */
    private fun collectForbidden(
        type: Type,
        location: String,
        leaks: MutableList<String>,
        visiting: MutableSet<Type>,
    ) {
        if (!visiting.add(type)) {
            return
        }
        try {
            when (type) {
                is Class<*> -> collectForbiddenClass(type, location, leaks, visiting)

                is ParameterizedType -> {
                    collectForbidden(type.rawType, "$location raw", leaks, visiting)
                    type.ownerType?.let { owner ->
                        collectForbidden(owner, "$location owner", leaks, visiting)
                    }
                    type.actualTypeArguments.forEachIndexed { index, argument ->
                        collectForbidden(argument, "$location<$index>", leaks, visiting)
                    }
                }

                is GenericArrayType ->
                    collectForbidden(type.genericComponentType, "$location[]", leaks, visiting)

                is WildcardType -> {
                    type.upperBounds.forEachIndexed { index, bound ->
                        collectForbidden(bound, "$location extends[$index]", leaks, visiting)
                    }
                    type.lowerBounds.forEachIndexed { index, bound ->
                        collectForbidden(bound, "$location super[$index]", leaks, visiting)
                    }
                }

                is TypeVariable<*> -> type.bounds.forEachIndexed { index, bound ->
                    collectForbidden(bound, "$location bound[$index]", leaks, visiting)
                }

                else -> recordIfForbidden(type, location, leaks)
            }
        } finally {
            visiting.remove(type)
        }
    }

    private fun collectForbiddenClass(
        type: Class<*>,
        location: String,
        leaks: MutableList<String>,
        visiting: MutableSet<Type>,
    ) {
        val componentType = type.componentType
        if (componentType != null) {
            collectForbidden(componentType, "$location[]", leaks, visiting)
        } else {
            recordIfForbidden(type, location, leaks)
        }
    }

    private fun recordIfForbidden(type: Type, location: String, leaks: MutableList<String>) {
        val typeName = type.typeName
        if (typeName.isForbiddenType()) {
            leaks += "$location -> $typeName"
        }
    }

    private fun newVisitingSet(): MutableSet<Type> =
        Collections.newSetFromMap(IdentityHashMap<Type, Boolean>())

    private fun String.isForbiddenType(): Boolean = forbiddenPrefixes.any { startsWith(it) }

    /** Minimal [Type] whose name comes from a string, used to model unavailable gateway types. */
    private class NamedType(private val name: String) : Type {
        override fun toString(): String = name
    }

    private class SyntheticParameterizedType(
        private val raw: Type,
        private val arguments: List<Type>,
        private val owner: Type? = null,
    ) : ParameterizedType {
        override fun getRawType(): Type = raw

        override fun getOwnerType(): Type? = owner

        override fun getActualTypeArguments(): Array<Type> = arguments.toTypedArray()

        override fun toString(): String =
            arguments.joinToString(prefix = "${raw.typeName}<", postfix = ">")
    }

    private class SyntheticGenericArrayType(private val component: Type) : GenericArrayType {
        override fun getGenericComponentType(): Type = component

        override fun toString(): String = "${component.typeName}[]"
    }

    private class SyntheticWildcardType(
        private val upperBounds: List<Type>,
        private val lowerBounds: List<Type>,
    ) : WildcardType {
        override fun getUpperBounds(): Array<Type> = upperBounds.toTypedArray()

        override fun getLowerBounds(): Array<Type> = lowerBounds.toTypedArray()

        override fun toString(): String = when {
            lowerBounds.isNotEmpty() -> "? super ${lowerBounds.first().typeName}"
            upperBounds.isNotEmpty() -> "? extends ${upperBounds.first().typeName}"
            else -> "?"
        }
    }

    private class SyntheticTypeVariable(
        private val variableName: String,
        private val declaration: GenericDeclaration,
        private val bounds: List<Type>,
    ) : TypeVariable<GenericDeclaration> {
        override fun getBounds(): Array<Type> = bounds.toTypedArray()

        override fun getGenericDeclaration(): GenericDeclaration = declaration

        override fun getAnnotatedBounds(): Array<AnnotatedType> = emptyArray()

        override fun getName(): String = variableName

        override fun <T : Annotation> getAnnotation(annotationClass: Class<T>): T =
            throw UnsupportedOperationException("annotations are irrelevant to this reflection walk")

        override fun getAnnotations(): Array<out Annotation> = emptyArray()

        override fun getDeclaredAnnotations(): Array<out Annotation> = emptyArray()

        override fun toString(): String = variableName
    }

    private object CyclicTypeVariable : TypeVariable<GenericDeclaration> {
        override fun getBounds(): Array<Type> = arrayOf(this)

        override fun getGenericDeclaration(): GenericDeclaration =
            VoiceBoundaryReflectionTest::class.java

        override fun getAnnotatedBounds(): Array<AnnotatedType> = emptyArray()

        override fun getName(): String = "T"

        override fun <T : Annotation> getAnnotation(annotationClass: Class<T>): T =
            throw UnsupportedOperationException("annotations are irrelevant to this reflection walk")

        override fun getAnnotations(): Array<out Annotation> = emptyArray()

        override fun getDeclaredAnnotations(): Array<out Annotation> = emptyArray()

        override fun toString(): String = "T"
    }
}
