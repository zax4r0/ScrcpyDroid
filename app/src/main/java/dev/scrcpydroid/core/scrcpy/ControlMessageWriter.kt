package dev.scrcpydroid.core.scrcpy

import okio.BufferedSink

class ControlMessageWriter(
    private val sink: BufferedSink
) {
    companion object {
        const val TYPE_INJECT_KEYCODE = 0x00
        const val TYPE_INJECT_TOUCH_EVENT = 0x02
        const val TYPE_SET_DISPLAY_POWER = 0x0A
        const val TYPE_ROTATE_DEVICE = 0x0B

        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2

        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_APP_SWITCH = 187
    }

    fun injectTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 1,
        buttons: Int = 1
    ) = synchronized(sink) {
        sink.writeByte(TYPE_INJECT_TOUCH_EVENT)
        sink.writeByte(action)
        sink.writeLong(pointerId)
        sink.writeInt(x)
        sink.writeInt(y)
        sink.writeShort(screenWidth)
        sink.writeShort(screenHeight)
        val pressureShort = (pressure * 0xFFFF).toInt().toShort()
        sink.writeShort(pressureShort.toInt())
        sink.writeInt(actionButton)
        sink.writeInt(buttons)
        sink.flush()
    }

    fun injectKeycode(
        action: Int,
        keycode: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ) = synchronized(sink) {
        sink.writeByte(TYPE_INJECT_KEYCODE)
        sink.writeByte(action)
        sink.writeInt(keycode)
        sink.writeInt(repeat)
        sink.writeInt(metaState)
        sink.flush()
    }

    fun pressBack() {
        injectKeycode(action = ACTION_DOWN, keycode = KEYCODE_BACK)
        injectKeycode(action = ACTION_UP, keycode = KEYCODE_BACK)
    }

    fun pressHome() {
        injectKeycode(action = ACTION_DOWN, keycode = KEYCODE_HOME)
        injectKeycode(action = ACTION_UP, keycode = KEYCODE_HOME)
    }

    fun pressAppSwitch() {
        injectKeycode(action = ACTION_DOWN, keycode = KEYCODE_APP_SWITCH)
        injectKeycode(action = ACTION_UP, keycode = KEYCODE_APP_SWITCH)
    }

    fun setDisplayPower(on: Boolean) = synchronized(sink) {
        sink.writeByte(TYPE_SET_DISPLAY_POWER)
        sink.writeByte(if (on) 1 else 0)
        sink.flush()
    }

    fun rotateDevice() = synchronized(sink) {
        sink.writeByte(TYPE_ROTATE_DEVICE)
        sink.flush()
    }
}
