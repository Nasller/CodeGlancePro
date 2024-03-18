package com.nasller.codeglance.config.enums

import com.nasller.codeglance.util.message

interface BaseEnum {
	val messageCode: String

	fun getMessage(): String{
		return message(messageCode)
	}

	companion object{
		inline fun <reified T: Enum<T>> findEnum(message: String?): T{
			return enumValues<T>().find { (it as BaseEnum).getMessage() == message }!!
		}
	}
}