package xerus.monstercat.api.response

import com.google.api.client.util.Key
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

val KClass<*>.declaredKeys
	get() = memberProperties.mapNotNull {
		(it.javaField?.getDeclaredAnnotation(Key::class.java)
			?: return@mapNotNull null).value.takeUnless { it == "##default" } ?: it.name
	}

val <T: Any> KClass<T>.keyedProperties
	get() = memberProperties.filter {
		it.javaField?.getDeclaredAnnotation(Key::class.java) != null
	}.filterIsInstance<KMutableProperty<*>>()