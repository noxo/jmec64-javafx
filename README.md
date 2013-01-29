jmec64-javafx
=============
JME Commodore 64 emulator (by Joerg Jahnke) with JavaFX support.

Runs well on Raspberry PI and Java 1.8.0 Early Access (no SFX yet).

JavaFX 2.2 needed.

Running
=======
* java -jar jmec64-javafx.jar diskimage.d64
* select program to load from onscreen menu (up/down/enter)
* type "run" after load is complete, with "fastload" option program is loaded right after enter is pressed from menu.

Options
=======
-Dfastload=true|false      enables fastload<br>
-Dkeepaspect=true|false    keeps aspect ratio<br>
-Djoystick=0|1             select joystick port<br>

Special Keys
=============
ALT=Run/Stop<br>
CTRL=Joystick fire<br>
<br>
Have Fun :)
