package com.nasller.codeglance.config.enums

import com.nasller.codeglance.util.message

enum class ClickTypeEnum(private val messageCode:String){
	CODE_POSITION("settings.click.code"),
	MOUSE_POSITION("settings.click.mouse"),
	;

	fun getMessage():String{
		return message(messageCode)
	}

	companion object{
		fun findEnum(message:String?):ClickTypeEnum{
			return ClickTypeEnum.values().find { it.getMessage() == message }!!
		}
	}
}