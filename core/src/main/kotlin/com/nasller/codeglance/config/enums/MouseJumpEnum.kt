package com.nasller.codeglance.config.enums

enum class MouseJumpEnum(override val messageCode:String): BaseEnum{
	NONE("settings.jump.none"),
	MOUSE_DOWN("settings.jump.down"),
	MOUSE_UP("settings.jump.up"),
	;
}