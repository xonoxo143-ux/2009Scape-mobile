package MobileClientBindings

import plugin.Plugin
import plugin.annotations.PluginMeta
import plugin.api.API
import rt4.Keyboard
import rt4.Mouse
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

@PluginMeta(
        author = "downthecrop",
        description = "Ported modifications from legacy mobile miniclient",
        version = 1.0
)
class plugin : Plugin() {

    companion object {
        private var cameraToggle = false;
        private var rightClickToggle = false;
        private var lastMouseWheelX = 0
        private var lastMouseWheelY = 0
    }

    override fun Init() {
        // Unregister the default keyboard listener
        Keyboard.stop(rt4.GameShell.canvas)
        API.AddKeyboardListener(KeyboardCallback)
        API.AddMouseListener(MouseCallbacks)
    }

    object MouseCallbacks : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent?) {
            e ?: return
            if (cameraToggle) {
                val x = e.x
                val y = e.y
                val accelX = lastMouseWheelX - x
                val accelY = lastMouseWheelY - y
                lastMouseWheelX = x
                lastMouseWheelY = y
                API.UpdateCameraYaw(accelX * 2.0)
                API.UpdateCameraPitch(-accelY * 2.0)
            }
        }
        override fun mouseDragged(e: MouseEvent?) {
            e ?: return
            if (cameraToggle) {
                val x = e.x
                val y = e.y
                val accelX = lastMouseWheelX - x
                val accelY = lastMouseWheelY - y
                lastMouseWheelX = x
                lastMouseWheelY = y
                API.UpdateCameraYaw(accelX * 2.0)
                API.UpdateCameraPitch(-accelY * 2.0)
            }
        }

        override fun mouseClicked(e: MouseEvent?) {
            e ?: return
        }

        override fun mousePressed(e: MouseEvent?) {
            e ?: return
            if (cameraToggle) {
                lastMouseWheelX = e.x
                lastMouseWheelY = e.y
            }
            if (Mouse.instance != null) {
                Mouse.idleLoops = 0
                Mouse.eventMouseDownX  = e.x
                Mouse.eventMouseDownY = e.y
                Mouse.eventTime = MonotonicClock.currentTimeMillis()
                if (e.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK == 0 && !rightClickToggle) {
                    Mouse.eventButton  = 1
                    Mouse.eventAction  = 1
                } else {
                    rightClickToggle = false;
                    Mouse.eventButton = 2
                    Mouse.eventAction = 2
                }
            }
            if (e.isPopupTrigger) {
                e.consume()
            }
        }
    }

    object KeyboardCallback : KeyAdapter() {

        private var capitalize = false

        override fun keyTyped(e: KeyEvent?) {
            // The default RT4 keyboard logic.
            // for printable characters.
            if(e == null) return

            if(capitalize){
                capitalize = false;
                println("Replaced char: "+ e.keyChar);
                if(isSpecial(e.keyChar)){
                    e.keyChar = getSpecial(e.keyChar);
                } else {
                    e.keyChar = Character.toUpperCase(e.keyChar);
                }
                println("With char: "+ e.keyChar);
            }

            val c = Keyboard.getKeyChar(e)
            println("WRITING: "+e.keyChar);
            if (c >= 0) {
                val index = Keyboard.typedQueueWriterIndex + 1 and 0x7F
                if (Keyboard.typedQueueReaderIndex != index) {
                    Keyboard.typedCodeQueue[Keyboard.typedQueueWriterIndex] = -1
                    Keyboard.typedCharQueue[Keyboard.typedQueueWriterIndex] = c
                    Keyboard.typedQueueWriterIndex = index
                }
            }
            e.consume()
        }

        override fun keyPressed(e: KeyEvent?) {
            // The default RT4 keyboard logic.
            // for delete key ect.
            if(e == null) return

            if(e.keyCode == KeyEvent.VK_RIGHT) {
                API.UpdateCameraYaw( 15.0)
                return;
            }
            if(e.keyCode == KeyEvent.VK_LEFT) {
                API.UpdateCameraYaw(-15.0)
                return;
            }

            if(e.keyCode == KeyEvent.VK_UP) {
                API.UpdateCameraPitch( 15.0)
                return;
            }
            if(e.keyCode == KeyEvent.VK_DOWN) {
                API.UpdateCameraPitch(-15.0)
                return;
            }

            if(e.keyCode == KeyEvent.VK_F3) {
                API.UpdateCameraZoom(-1)
                return;
            }
            if(e.keyCode == KeyEvent.VK_F4) {
                API.UpdateCameraZoom(1)
                return;
            }
            if(e.keyCode == KeyEvent.VK_F5) {
                Mouse.clickX = Mouse.lastMouseX;
                Mouse.clickY = Mouse.lastMouseY;
                Mouse.isDragClick = !Mouse.isDragClick;
                return;
            }
            if(e.keyCode == KeyEvent.VK_F8) {
                cameraToggle = false;
                return;
            }
            if(e.keyCode == KeyEvent.VK_F9) {
                cameraToggle = true;
                return;
            }
            if(e.keyCode == KeyEvent.VK_F10) {
                rightClickToggle = false;
                return;
            }
            if(e.keyCode == KeyEvent.VK_F11) {
                rightClickToggle = true;
                return;
            }
            if (e.keyCode == KeyEvent.VK_F12) {
                capitalize = true;
                return;
            }

            Keyboard.idleLoops = 0
            var code: Int = e.keyCode
            if (code >= 0 && Keyboard.CODE_MAP.size > code) {
                code = Keyboard.CODE_MAP[code]
                if (code and 0x80 != 0) {
                    code = -1
                }
            } else {
                code = -1
            }

            if (Keyboard.eventQueueWriterIndex >= 0 && code >= 0) {
                Keyboard.eventQueue[Keyboard.eventQueueWriterIndex] = code
                Keyboard.eventQueueWriterIndex = Keyboard.eventQueueWriterIndex + 1 and 0x7F
                if (Keyboard.eventQueueWriterIndex == Keyboard.eventQueueReaderIndex) {
                    Keyboard.eventQueueWriterIndex = -1
                }
            }

            if (code >= 0) {

                val index = Keyboard.typedQueueWriterIndex + 1 and 0x7F
                if (index != Keyboard.typedQueueReaderIndex) {
                    Keyboard.typedCodeQueue[Keyboard.typedQueueWriterIndex] = code
                    Keyboard.typedCharQueue[Keyboard.typedQueueWriterIndex] = -1
                    Keyboard.typedQueueWriterIndex = index
                }
            }

            val modifiers: Int = e.modifiersEx
            if (modifiers and 0xA != 0 || code == Keyboard.KEY_BACK_SPACE || code == Keyboard.KEY_ENTER) {
                e.consume()
            }
        }

        override fun keyReleased(e: KeyEvent?) {

        }

        fun getSpecial(c: Char): Char {
            when (c) {
                '1' -> return '!'
                '2' -> return '@'
                '3' -> return '#'
                '4' -> return '$'
                '5' -> return '%'
                '6' -> return '^'
                '7' -> return '&'
                '8' -> return '*'
                '9' -> return '('
                '0' -> return ')'
                '-' -> return '_'
                '=' -> return '+'
                '[' -> return '{'
                ']' -> return '}'
                ';' -> return ':'
                '\'' -> return '"'
                ',' -> return '<'
                '.' -> return '>'
                '/' -> return '?'
                '\\' -> return '|'
            }
            return '~'
        }

        fun isSpecial(c: Char): Boolean {
            val specialChars = "/*!@#$%^&*:();\"{}_[+=-_]\'|\\?/<>,."
            return Character.isDigit(c) || specialChars.contains("" + c)
        }
    }

    object MonotonicClock {
        private var leapMillis: Long = 0

        private var previous: Long = 0
        @Synchronized
        fun currentTimeMillis(): Long {
            val now = System.currentTimeMillis()
            if (previous > now) {
                leapMillis += previous - now
            }
            previous = now
            return leapMillis + now
        }
    }
}