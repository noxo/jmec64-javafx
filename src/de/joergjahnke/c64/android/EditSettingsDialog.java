package de.joergjahnke.c64.android;

import de.joergjahnke.c64.core.C1541;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import java.util.HashMap;
import java.util.Map;

/**
 * Class displaying a dialog where the user can enter the text
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class EditSettingsDialog extends Activity {

    /**
     * Maps the joystick port numbers to joystick port IDs from the resource file and vice versa
     */
    private final static Map<Integer, Integer> joystickPortIdMap = new HashMap<Integer, Integer>();


    static {
        joystickPortIdMap.put(0, R.id.port1);
        joystickPortIdMap.put(1, R.id.port2);
        joystickPortIdMap.put(R.id.port1, 0);
        joystickPortIdMap.put(R.id.port2, 1);
    }
    /**
     * Maps the joystick port numbers to joystick port IDs from the resource file and vice versa
     */
    private final static Map<Integer, Integer> frameSkipIdMap = new HashMap<Integer, Integer>();


    static {
        frameSkipIdMap.put(0, R.id.fsauto);
        frameSkipIdMap.put(1, R.id.fs1);
        frameSkipIdMap.put(2, R.id.fs2);
        frameSkipIdMap.put(3, R.id.fs3);
        frameSkipIdMap.put(4, R.id.fs4);
        frameSkipIdMap.put(R.id.fsauto, 0);
        frameSkipIdMap.put(R.id.fs1, 1);
        frameSkipIdMap.put(R.id.fs2, 2);
        frameSkipIdMap.put(R.id.fs3, 3);
        frameSkipIdMap.put(R.id.fs4, 4);
    }
    /**
     * Maps the joystick port numbers to joystick port IDs from the resource file and vice versa
     */
    private final static Map<Integer, Integer> driveModeIdMap = new HashMap<Integer, Integer>();


    static {
        driveModeIdMap.put(C1541.FAST_EMULATION, R.id.fast);
        driveModeIdMap.put(C1541.BALANCED_EMULATION, R.id.balanced);
        driveModeIdMap.put(C1541.COMPATIBLE_EMULATION, R.id.compatible);
        driveModeIdMap.put(R.id.fast, C1541.FAST_EMULATION);
        driveModeIdMap.put(R.id.balanced, C1541.BALANCED_EMULATION);
        driveModeIdMap.put(R.id.compatible, C1541.COMPATIBLE_EMULATION);
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.editsettingsdialog);

        // set default selections
        final RadioGroup joystickPortRadio = (RadioGroup) findViewById(R.id.joystickPort);
        final RadioGroup frameSkipRadio = (RadioGroup) findViewById(R.id.frameskip);
        final CheckBox antialiasingCheckBox = (CheckBox) findViewById(R.id.antialiasingActive);
        final RadioGroup driveModeRadio = (RadioGroup) findViewById(R.id.driveMode);
        final CheckBox soundCheckBox = (CheckBox) findViewById(R.id.soundActive);
        final CheckBox orientationSensorCheckBox = (CheckBox) findViewById(R.id.orientationSensorActive);

        joystickPortRadio.check(joystickPortIdMap.get(getIntent().getIntExtra("de.joergjahnke.c64.android.joystickPort", 0)));
        frameSkipRadio.check(frameSkipIdMap.get(getIntent().getIntExtra("de.joergjahnke.c64.android.frameSkip", 0)));
        antialiasingCheckBox.setChecked(getIntent().getBooleanExtra("de.joergjahnke.c64.android.antialiasing", false));
        driveModeRadio.check(driveModeIdMap.get(getIntent().getIntExtra("de.joergjahnke.c64.android.driveMode", C1541.BALANCED_EMULATION)));
        soundCheckBox.setChecked(getIntent().getBooleanExtra("de.joergjahnke.c64.android.soundActive", Integer.parseInt(android.os.Build.VERSION.SDK) >= 3));
        orientationSensorCheckBox.setChecked(getIntent().getBooleanExtra("de.joergjahnke.c64.android.orientationSensorActive", false));

        if (true || !((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getSensorList(Sensor.TYPE_ORIENTATION).isEmpty()) {
            findViewById(R.id.orientationSensorActiveText).setVisibility(View.GONE);
            orientationSensorCheckBox.setVisibility(View.GONE);
        }

        // install button listener which will take care of returning the results
        final Button okButton = (Button) findViewById(R.id.ok);

        okButton.setOnClickListener(
                new OnClickListener() {

                    public void onClick(View arg0) {
                        final Intent extras = new Intent();
                        final int joystickPortId = joystickPortRadio.getCheckedRadioButtonId();

                        extras.putExtra("de.joergjahnke.c64.android.joystickPort", joystickPortIdMap.get(joystickPortId));

                        final int frameSkipId = frameSkipRadio.getCheckedRadioButtonId();

                        extras.putExtra("de.joergjahnke.c64.android.frameSkip", frameSkipIdMap.get(frameSkipId));

                        final boolean useAntialiasing = antialiasingCheckBox.isChecked();

                        extras.putExtra("de.joergjahnke.c64.android.antialiasing", useAntialiasing);

                        final int driveModeId = driveModeRadio.getCheckedRadioButtonId();

                        extras.putExtra("de.joergjahnke.c64.android.driveMode", driveModeIdMap.get(driveModeId));

                        final boolean soundActive = soundCheckBox.isChecked();

                        extras.putExtra("de.joergjahnke.c64.android.soundActive", soundActive);

                        final boolean orientationSensorActive = orientationSensorCheckBox.isChecked();

                        extras.putExtra("de.joergjahnke.c64.android.orientationSensorActive", orientationSensorActive);

                        setResult(RESULT_OK, extras);
                        finish();
                    }
                });
    }
}
