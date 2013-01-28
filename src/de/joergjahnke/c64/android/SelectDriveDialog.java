package de.joergjahnke.c64.android;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Class displaying a dialog where the user can select the active C64 floppy drive
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SelectDriveDialog extends ListActivity {

    final static String[] DRIVE_OPTIONS = {"Drive 1 (#8)", "Drive 2 (#9)", "Drive 3 (#10)", "Drive 4 (#11)"};

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        ArrayAdapter<String> keysAdapter = new ArrayAdapter<String>(this, R.layout.listactivities_textview1, DRIVE_OPTIONS);
        setListAdapter(keysAdapter);
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        final Intent extras = new Intent();

        extras.putExtra("de.joergjahnke.c64.android.drive", position);

        setResult(RESULT_OK, extras);
        finish();
    }
}
