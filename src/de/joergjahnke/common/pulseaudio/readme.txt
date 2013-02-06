Experimental PulseAudio based audio backend
===========================================

JME C64 => PCM samples => TCP IP socket => PulseAudio server

1. Setting up on Raspberry PI
=============================
  
  http://raspberrypi.stackexchange.com/questions/639/how-to-get-pulseaudio-running

1) Install packages: sudo apt-get install pulseaudio pulseaudio-utils
2) ALSA and PulseAudio configuration in /etc/asound.conf

	pcm.mmap0 {
	    type mmap_emul;
	    slave {
	      pcm "hw:0,0";
	    }
	}
	
	#pcm.!default {
	#  type plug;
	#  slave {
	#    pcm mmap0;
	#  }
	#}
	
	pcm.pulse { type pulse }
	ctl.pulse { type pulse }
	pcm.!default { type pulse }
	ctl.!default { type pulse }

3) Comment this line in /etc/asound.conf 

  #load-module module-suspend-on-idle

4) Add TCP audio data listener configuration in /etc/pulse/default.pa
	
   load-module module-simple-protocol-tcp port=8081 rate=44100 format=s16le channels=1 record=yes

5) Add following parameters to /etc/pulse/audio.conf
 
   default-sample-rate = 48000
   resample-method = trivial
   
6) Reboot RPI

7) Start PulseAudio server

   pulseaudio -D (for more loggin run with -vvvv)

8) Start JavaFX JME-C64
 