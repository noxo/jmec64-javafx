package de.joergjahnke.c64.android;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import java.util.ArrayList;
import java.util.List;

/**
 * Class displaying a dialog for selecting the file to load from the image
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class LoadFileDialog extends ListActivity {

    /**
     * Prefix to the result string for fast-loading and running the selected file
     */
    public static final String LOADTYPE_FAST_LOAD_RUN = "fastLoadRun";
    /**
     * Prefix to the result string for normal loading of the selected file
     */
    public static final String LOADTYPE_NORMAL_LOAD = "load";
    /**
     * Prefix to the result string for fast-loading the selected file
     */
    public static final String LOADTYPE_FAST_LOAD = "fastLoad";
    /**
     * Currently displayed files
     */
    private final List<String> currentFiles = new ArrayList<String>();

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.loadfiledialog);

        // get all files from the C64 image
        this.currentFiles.addAll((List) getIntent().getExtras().get("de.joergjahnke.c64.android.files"));

        // display the files for selection
        ArrayAdapter<String> filenamesAdapter = new ArrayAdapter<String>(this, R.layout.listactivities_textview1, this.currentFiles);

        setListAdapter(filenamesAdapter);
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        // return load-type + ':' + name of file to load as result
        final RadioGroup loadTypeRadio = (RadioGroup) findViewById(R.id.loadType);
        String loadType = null;

        switch (loadTypeRadio.getCheckedRadioButtonId()) {
            case R.id.fastLoad:
                loadType = LOADTYPE_FAST_LOAD;
                break;
            case R.id.load:
                loadType = LOADTYPE_NORMAL_LOAD;
                break;
            default:
                loadType = LOADTYPE_FAST_LOAD_RUN;
        }

        final Intent extras = new Intent();

        extras.putExtra("de.joergjahnke.c64.android.loadType", loadType);
        extras.putExtra("de.joergjahnke.c64.android.selectedFile", this.currentFiles.get(position));

        setResult(RESULT_OK, extras);
        finish();
    }
}
