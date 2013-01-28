package de.joergjahnke.c64.android;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Class displaying a dialog where the user can select a special C64 key
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SpecialKeysDialog extends ListActivity {

    final static String[] SPECIAL_KEYS = {"Escape", "Run", "F1", "F3", "F5", "F7", "Space", "Enter", "Delete", "Break", "Commodore", "Pound", "Cursor Up", "Cursor Down", "Cursor Left", "Cursor Right"};

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        ArrayAdapter<String> keysAdapter = new ArrayAdapter<String>(this, R.layout.listactivities_textview1, SPECIAL_KEYS);
        setListAdapter(keysAdapter);
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        final Intent extras = new Intent();

        extras.putExtra("de.joergjahnke.c64.android.selectedKey", SPECIAL_KEYS[position]);

        setResult(RESULT_OK, extras);
        finish();
    }
}
