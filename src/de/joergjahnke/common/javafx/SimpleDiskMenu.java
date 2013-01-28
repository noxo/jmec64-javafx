package de.joergjahnke.common.javafx;

import java.io.IOException;
import java.util.Vector;

import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.extendeddevices.EmulatorUtils;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

//Erkki Nokso-Koivisto 28/Jan/2013


public class SimpleDiskMenu {

	private static boolean active;
	private static C64 c64;
	private static Vector<String> programs;
	private static int selectedProgram;
	private static boolean fast;
	final static int overScanPadding = 50;

	public static void loadDisk(String diskFilename, C64 c64, boolean fast) 
			throws Exception{
		SimpleDiskMenu.c64 = c64;
		SimpleDiskMenu.fast = fast;
		EmulatorUtils.attachImage(c64, 0, diskFilename);
		programs = c64.getDrive(c64.getActiveDrive()).getFilenames();
		active = true;
	}

	public static void keyPressed(KeyCode keyCode) {
		
		if (keyCode == KeyCode.UP) {
			
			selectedProgram--;
			
			if (selectedProgram < 0)
				selectedProgram = 0;
		}
		else if (keyCode == KeyCode.DOWN) {
			
			selectedProgram++;
			
			if (selectedProgram > programs.size()-1)
				selectedProgram = programs.size()-1;
			
		}
		else if (keyCode == KeyCode.ENTER) {
			
			try {
				
				String loadFilename = programs.elementAt(selectedProgram);
				
				if (fast) {
					c64.fastLoadFile(loadFilename, -1);
				}
				else {
					c64.loadFile(loadFilename);
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			active = false;
		}
		else if (keyCode == KeyCode.ESCAPE) {
			active = false;
		}
			
	}
	
	
	public static void drawDiskMenu(GraphicsContext gc) {

		try {

			for (int i = 0; i < programs.size(); i++) {
				String program = programs.elementAt(i);
				gc.setFill(i == selectedProgram ? Color.WHITE : Color.BLACK);
				gc.fillText(program, overScanPadding, overScanPadding + (10 * (i + 1)));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static boolean isActive() {
		return active;
	}
}
