package de.joergjahnke.common.android;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class displaying a dialog for selecting a file
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class FileDialog extends ListActivity {

    /**
     * text we use for the parent directory
     */
    private final static String PARENT_DIR = "..";
    /**
     * Currently displayed files
     */
    protected final List<String> currentFiles = new ArrayList<String>();
    /**
     * Currently displayed directory
     */
    private File currentDir = null;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        // go to the root directory
        try {
            showDirectory(getIntent().getStringExtra("de.joergjahnke.c64.android.prgdir"));
        } catch (NullPointerException e) {
            showDirectory("/");
        }
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        if (position == 0 && PARENT_DIR.equals(this.currentFiles.get(0))) {
            showDirectory(this.currentDir.getParent());
        } else {
            final File file = new File(this.currentFiles.get(position));

            if (file.isDirectory()) {
                showDirectory(file.getAbsolutePath());
            } else {
            	final Intent extras = new Intent();
            	
            	extras.putExtra("de.joergjahnke.gameboy.android.currentFile", this.currentFiles.get(position));
                setResult(RESULT_OK, extras);
                finish();
            }
        }
    }

    /**
     * Show the contents of a given directory as a selectable list
     * 
     * @param path	the directory to display
     */
    private void showDirectory(final String path) {
    	retrieveDirectory(path);

        // display these images
        final Context context = this;

        ArrayAdapter<String> filenamesAdapter = new ArrayAdapter<String>(this, getTextView(), this.currentFiles) {

            @Override
            public View getView(final int position, final View convertView, final ViewGroup parent) {
                return new IconifiedTextLayout(context, getItem(position), position);
            }
        };

        setListAdapter(filenamesAdapter);
    }
    
    /**
     * Retrieve the contents of a given directory
     * 
     * @param path	the directory to display
     */
    protected void retrieveDirectory(final String path) {
        // we clear any old content and add an entry to get up one level
        this.currentFiles.clear();
        this.currentDir = new File(path);
        if (this.currentDir.getParentFile() != null) {
            this.currentFiles.add(PARENT_DIR);
        }

        // get all directories and C64 files in the given path
        final File[] files = this.currentDir.listFiles();
        final Set<String> sorted = new TreeSet<String>();

        for (final File file : files) {
            final String name = file.getAbsolutePath();

            if (file.isDirectory()) {
                sorted.add(name);
            } else {
                final String extension = name.indexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1) : "";

                if (null == getAcceptedFileTypes() || getAcceptedFileTypes().contains(extension.toLowerCase())) {
                    sorted.add(name);
                }
            }
        }
        this.currentFiles.addAll(sorted);
    }

    // new layout displaying a text and an associated image 
    class IconifiedTextLayout extends LinearLayout {

        public IconifiedTextLayout(final Context context, final String path, final int position) {
            super(context);

            setOrientation(HORIZONTAL);

            // determine icon to display
            final ImageView imageView = new ImageView(context);
            final File file = new File(path);

            if (position == 0 && PARENT_DIR.equals(path)) {
                imageView.setImageResource(getParentFolderImage());
            } else {
                if (file.isDirectory()) {
                    imageView.setImageResource(getFolderImage());
                } else {
                    imageView.setImageResource(getFileImage());
                }
            }
            imageView.setPadding(0, 1, 5, 0);

            // create view for the directory name
            final TextView textView = new TextView(context);

            textView.setText(file.getName());

            addView(imageView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            addView(textView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    // methods to be implemented by subclasses
    /**
     * Get the list of file extensions which are accepted by the file dialog
     * 
     * @return	null to accept all files, or a list of lowercase file extensions
     */
    public abstract List<String> getAcceptedFileTypes();

    /**
     * Get the TextView resource used for the TextView inside the list
     * 
     * @return	layout resource id
     */
    public abstract int getTextView();

    /**
     * Get the image used for navigating to the parent folder
     * 
     * @return	image resource id
     */
    public abstract int getParentFolderImage();

    /**
     * Get the image denoting a folder
     * 
     * @return	image resource id
     */
    public abstract int getFolderImage();

    /**
     * Get the image denoting a file to select
     * 
     * @return	image resource id
     */
    public abstract int getFileImage();
}
