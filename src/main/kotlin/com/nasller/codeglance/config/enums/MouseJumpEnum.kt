package com.nasller.codeglance.config.enums

import com.nasller.codeglance.util.message

enum class MouseJumpEnum(private val messageCode:String){
	NONE("settings.jump.none"),
	MOUSE_DOWN("settings.jump.down"),
	MOUSE_UP("settings.jump.up"),
	;

	fun getMessage():String{
		return message(messageCode)
	}

	companion object{
		fun findEnum(message:String?):MouseJumpEnum{
			return MouseJumpEnum.values().find { it.getMessage() == message }!!
		}
	}
}