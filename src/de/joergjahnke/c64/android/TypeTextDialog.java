package de.joergjahnke.c64.android;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

/**
 * Class displaying a dialog where the user can enter the text
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class TypeTextDialog extends ListActivity {

    /**
     * Previously typed texts
     */
    private final List<String> previousTexts = new ArrayList<String>();

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.typetextdialog);

        // get all previously typed texts
        this.previousTexts.addAll((List) getIntent().getExtras().get("de.joergjahnke.c64.android.oldTexts"));

        // display these texts for selection
        ArrayAdapter<String> filenamesAdapter = new ArrayAdapter<String>(this, R.layout.listactivities_textview1, this.previousTexts);

        setListAdapter(filenamesAdapter);

        // have buttons react properly
        final EditText textField = (EditText) findViewById(R.id.typedtext);
        final Button okButton = (Button) findViewById(R.id.ok);
        final Button okEnterButton = (Button) findViewById(R.id.okEnter);

        okButton.setOnClickListener(
                new OnClickListener() {

                    public void onClick(View arg0) {
                        final Intent extras = new Intent();

                        extras.putExtra("de.joergjahnke.c64.android.typedText", textField.getText().toString());

                        setResult(RESULT_OK, extras);

                        finish();
                    }
                });
        okEnterButton.setOnClickListener(
                new OnClickListener() {

                    public void onClick(View arg0) {
                        final Intent extras = new Intent();

                        extras.putExtra("de.joergjahnke.c64.android.typedText", textField.getText().toString() + '\n');

                        setResult(RESULT_OK, extras);

                        finish();
                    }
                });
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        // we copy the old text into the text field
        final EditText textField = (EditText) findViewById(R.id.typedtext);

        textField.setText(this.previousTexts.get(position));
    }
}
