package de.joergjahnke.c64.android;

import de.joergjahnke.c64.core.C1541;
import de.joergjahnke.common.android.FileDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * Class displaying a dialog for selecting a C64 image to attach to the emulator
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class AttachImageDialog extends FileDialog {

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAcceptedFileTypes() {
        final List<String> types = new ArrayList<String>();

        types.addAll(C1541.SUPPORTED_EXTENSIONS);
        types.add(AndroidC64.DELTA_FILE_EXTENSION);

        return types;
    }

    @Override
    public int getFileImage() {
        return R.drawable.floppy;
    }

    @Override
    public int getFolderImage() {
        return R.drawable.folder;
    }

    @Override
    public int getParentFolderImage() {
        return R.drawable.parent;
    }

    @Override
    public int getTextView() {
        return R.layout.listactivities_textview1;
    }
}
