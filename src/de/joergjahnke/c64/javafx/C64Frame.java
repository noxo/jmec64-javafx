package de.joergjahnke.c64.javafx;

import java.nio.IntBuffer;
import java.util.Hashtable;

import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.Joystick;
import de.joergjahnke.c64.core.VIC6569;
import de.joergjahnke.common.javafx.SimpleDiskMenu;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.common.vmabstraction.sunvm.SunVMResourceLoader;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

// Erkki Nokso-Koivisto 28/Jan/2013

public class C64Frame extends javafx.application.Application implements Observer {
	
    // maps key codes from key events to joystick movements of the C64
    final static Hashtable<KeyCode, Integer> keycodeJoystickMap = new Hashtable<KeyCode, Integer>();
    final WritableImage frameBuffer = new WritableImage(400,280);

    Canvas canvas;
    C64 c64;
        
    private final static Hashtable<KeyCode, String> keycodeKeyMap = new Hashtable<KeyCode, String>();

    static {
        keycodeKeyMap.put(KeyCode.SHIFT, "SHIFT");
        keycodeKeyMap.put(KeyCode.ALT, "RUN");
        keycodeKeyMap.put(KeyCode.F1, "F1");
        keycodeKeyMap.put(KeyCode.F3, "F3");
        keycodeKeyMap.put(KeyCode.F5, "F5");
        keycodeKeyMap.put(KeyCode.F7, "F7");
        keycodeKeyMap.put(KeyCode.BACK_SPACE, "BACKSPACE");
        keycodeKeyMap.put(KeyCode.ENTER, "ENTER");
    } 
        
    static {
        keycodeJoystickMap.put(KeyCode.LEFT, new Integer(Joystick.LEFT));
        keycodeJoystickMap.put(KeyCode.RIGHT, new Integer(Joystick.RIGHT));
        keycodeJoystickMap.put(KeyCode.UP, new Integer(Joystick.UP));
        keycodeJoystickMap.put(KeyCode.DOWN, new Integer(Joystick.DOWN));
        keycodeJoystickMap.put(KeyCode.CONTROL, new Integer(Joystick.FIRE));
    }
    
	
	@Override
	public void start(Stage stage) throws Exception {
		
    	c64 = new C64(new SunVMResourceLoader());
        c64.getVIC().setFrameSkip(1);

        final VIC6569 vic = c64.getVIC();

        vic.addObserver(this);
        vic.initScreenMemory();
        
        
        // create a player that observes the SID and plays its sound
        //c64.getSID().addObserver(new WavePlayer(this.c64.getSID()));
        
        // set joystick port
        
        try {
        	int activeJoystick = Integer.valueOf(System.getProperty("joystick"));
        	c64.setActiveJoystick(activeJoystick);
        } catch (Exception ex) {
            c64.setActiveJoystick(1);
        }
        
        // enable fastload
        
        boolean fastload = false;
        
        try {
        	fastload = Boolean.valueOf(System.getProperty("fastload"));
        } catch (Exception ex) {
        	// 
        }
        
        // attach disk to drive if defined in args
        
        if ( getParameters().getRaw().size() > 0) {
        	SimpleDiskMenu.loadDisk(getParameters().getRaw().get(0), c64, fastload);
        }
        
        boolean keepAspect = true;

        try {
        	keepAspect = Boolean.valueOf(System.getProperty("keepaspect"));
        } catch (Exception ex) {
        	//
        }
        
        Rectangle2D screenSize = Screen.getPrimary().getBounds();       
        
        if (keepAspect) {
        	
        	double d = (double) 400 / 280;
        	double w = (double) screenSize.getHeight() * d;
        	double h = (double) screenSize.getHeight();       	
        	canvas = new Canvas(w, h);
        	canvas.setTranslateX(screenSize.getWidth()/2 - w/2);
        	
        }
        else {
    		canvas = new Canvas(screenSize.getWidth(), screenSize.getHeight());
        }

        stage.setFullScreen(true);
		
		Group gameNode = new Group(canvas);
		Scene gameScene = new Scene(gameNode);
		gameScene.setFill(Color.BLACK);
		
		gameScene.setOnKeyReleased( new EventHandler<KeyEvent>() {

			@Override
			public void handle(KeyEvent event) {
				handleKeyReleased(event);			}
		});
		
		gameScene.setOnKeyPressed( new EventHandler<KeyEvent>() {

			@Override
			public void handle(KeyEvent event) {
				handleKeyPressed(event);
			}

		});
		
		stage.setScene(gameScene);
		stage.show();
		
		new Thread(c64).start();
		
	}
	
	protected void handleKeyPressed(KeyEvent event) {
		
		KeyCode keyCode = event.getCode();
		Integer joy = keycodeJoystickMap.get(keyCode);
		String key = keycodeKeyMap.get(keyCode);
		
		if ( SimpleDiskMenu.isActive() ) {
			SimpleDiskMenu.keyPressed(keyCode);
			return;
		}
		
		if (keyCode == KeyCode.ESCAPE) {
			Platform.exit();
			c64.stop();
			return;
		}
		
		if (joy != null) {
			
            final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());
            final int value = joy.intValue();

            joystick.setDirection(joystick.getDirection() | (value & Joystick.DIRECTIONS));
            if ((value & Joystick.FIRE) != 0) {
                joystick.setFiring(true);
            }
			
		}
		else if (key != null) {
			c64.getKeyboard().keyPressed(key);
		}
		else {
			if (keyCode != KeyCode.UNDEFINED) {
				c64.getKeyboard().keyPressed(event.getText());
			}
		}
        
	}
	
	protected void handleKeyReleased(KeyEvent event) {
		
		KeyCode keyCode = event.getCode();
		Integer joy = keycodeJoystickMap.get(keyCode);
		String key = keycodeKeyMap.get(keyCode);
		
		if ( SimpleDiskMenu.isActive() ) {
			return;
		}

        if (null != joy) {

                final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());
                final int value = joy.intValue();

                joystick.setDirection(joystick.getDirection() & ~(value & Joystick.DIRECTIONS));
                if ((value & Joystick.FIRE) != 0) {
                    joystick.setFiring(false);
                }
            
        } 
        else if (key != null) {
        	c64.getKeyboard().keyReleased(key);
        }
        else {
        	if (keyCode != KeyCode.UNDEFINED) {
        		c64.getKeyboard().keyReleased(event.getText());
        	}
        }

        
	}

	public static void main(String arg[]) {
		launch(arg);
	}
	
	final javafx.scene.image.PixelFormat<IntBuffer> format = WritablePixelFormat.getIntArgbInstance();
	
	final Runnable painter = new Runnable() {
		
		@Override
		public void run() {
			
            final VIC6569 vic = c64.getVIC();
            
            frameBuffer.getPixelWriter().setPixels(0, 
            								0, 
            								vic.getBorderWidth(), 
            								vic.getBorderHeight(),  
            								format, // format
            								vic.getRGBData(), // data
            								0, // offset
            								vic.getBorderWidth() // stride
            								);
            
			GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.drawImage(frameBuffer, 0,0,canvas.getWidth(),canvas.getHeight());
			
			if ( SimpleDiskMenu.isActive() ) {
				SimpleDiskMenu.drawDiskMenu(gc);
			}
				
		}
	};

	@Override
	public void update(Object observable, Object event) {
        // the notification comes from a VIC6569?
        if (observable instanceof VIC6569) {
            // the border color was changed?
            if (event instanceof de.joergjahnke.common.ui.Color) {
            // ignore this as this is covered in the normal screen paint operations
            } else {
                // repaint the screen;
                Platform.runLater(painter);
            }
        }
	}
	

}
