package com.nasller.codeglance.config.enums

enum class ClickTypeEnum(override val messageCode:String): BaseEnum{
	CODE_POSITION("settings.click.code"),
	MOUSE_POSITION("settings.click.mouse"),
	;
}