/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Implements the C64's keyboard.<br>
 * <br>
 * For documentation on the C64 keyboard handling, see <a href='http://www.zimmers.net/anonftp/pub/cbm/c64/programming/documents/keymatrix.txt'>http://www.zimmers.net/anonftp/pub/cbm/c64/programming/documents/keymatrix.txt</a>
 * or <a href='http://www.zimmers.net/anonftp/pub/cbm/magazines/transactor/v5i5/p039.jpg'>http://www.zimmers.net/anonftp/pub/cbm/magazines/transactor/v5i5/p039.jpg</a>.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Keyboard implements Serializable {

    /**
     * Maximum number of entries in the history of typed texts
     */
    private final static int MAX_HISTORY = 100;
    /**
     * Maps keys to KeyTableEntries
     */
    private final static Hashtable keyMap = new Hashtable();
    /**
     * Keyboard matrix
     */
    private int rowMatrix[] = new int[8];
    /**
     * Reversed keyboard matrix
     */
    private int columnMatrix[] = new int[8];
    /**
     * An internal buffer for keys that have been typed
     */
    private final Vector typedKeysBuffer = new Vector();
    /**
     * Are we in the process of typing a key
     */
    private boolean isTyping = false;
    /**
     * Delay until we release a pressed key when doing automatic typing
     */
    private int typeDelay = 0;
    /**
     * List of previously typed texts
     */
    private final Vector typedTexts = new Vector();
    /**
     * Text being currently typed
     */
    private final StringBuffer currentText = new StringBuffer();
    /**
     * Last pressed key
     */
    private String lastKey = null;
    /**
     * CIA has read key matrix data?
     */
    private boolean hasCIARead = false;
    /**
     * shift key is pressed?
     */
    private boolean isShiftPressed = false;

    /**
     * Creates a new keyboard attached to a given CIA
     */
    public Keyboard() {
        reset();
        if (keyMap.isEmpty()) {
            initializeKeyMap();
        }
    }

    /**
     * Reset the keyboard
     */
    public void reset() {
        for (int i = 0; i < 8; ++i) {
            this.rowMatrix[i] = 0xff;
            this.columnMatrix[i] = 0xff;
        }
        this.isTyping = false;
        this.isShiftPressed = false;
        this.typedKeysBuffer.removeAllElements();
        this.currentText.delete(0, this.currentText.length());
    }

    /**
     * Initailize the key map with all available keys
     */
    private void initializeKeyMap() {
        keyMap.put("a", new KeyTableEntry(1, 2, false));
        keyMap.put("A", new KeyTableEntry(1, 2, false));
        keyMap.put("b", new KeyTableEntry(3, 4, false));
        keyMap.put("B", new KeyTableEntry(3, 4, false));
        keyMap.put("c", new KeyTableEntry(2, 4, false));
        keyMap.put("C", new KeyTableEntry(2, 4, false));
        keyMap.put("d", new KeyTableEntry(2, 2, false));
        keyMap.put("D", new KeyTableEntry(2, 2, false));
        keyMap.put("e", new KeyTableEntry(1, 6, false));
        keyMap.put("E", new KeyTableEntry(1, 6, false));
        keyMap.put("f", new KeyTableEntry(2, 5, false));
        keyMap.put("F", new KeyTableEntry(2, 5, false));
        keyMap.put("g", new KeyTableEntry(3, 2, false));
        keyMap.put("G", new KeyTableEntry(3, 2, false));
        keyMap.put("h", new KeyTableEntry(3, 5, false));
        keyMap.put("H", new KeyTableEntry(3, 5, false));
        keyMap.put("i", new KeyTableEntry(4, 1, false));
        keyMap.put("I", new KeyTableEntry(4, 1, false));
        keyMap.put("j", new KeyTableEntry(4, 2, false));
        keyMap.put("J", new KeyTableEntry(4, 2, false));
        keyMap.put("k", new KeyTableEntry(4, 5, false));
        keyMap.put("K", new KeyTableEntry(4, 5, false));
        keyMap.put("l", new KeyTableEntry(5, 2, false));
        keyMap.put("L", new KeyTableEntry(5, 2, false));
        keyMap.put("m", new KeyTableEntry(4, 4, false));
        keyMap.put("M", new KeyTableEntry(4, 4, false));
        keyMap.put("n", new KeyTableEntry(4, 7, false));
        keyMap.put("N", new KeyTableEntry(4, 7, false));
        keyMap.put("o", new KeyTableEntry(4, 6, false));
        keyMap.put("O", new KeyTableEntry(4, 6, false));
        keyMap.put("p", new KeyTableEntry(5, 1, false));
        keyMap.put("P", new KeyTableEntry(5, 1, false));
        keyMap.put("q", new KeyTableEntry(7, 6, false));
        keyMap.put("Q", new KeyTableEntry(7, 6, false));
        keyMap.put("r", new KeyTableEntry(2, 1, false));
        keyMap.put("R", new KeyTableEntry(2, 1, false));
        keyMap.put("s", new KeyTableEntry(1, 5, false));
        keyMap.put("S", new KeyTableEntry(1, 5, false));
        keyMap.put("t", new KeyTableEntry(2, 6, false));
        keyMap.put("T", new KeyTableEntry(2, 6, false));
        keyMap.put("u", new KeyTableEntry(3, 6, false));
        keyMap.put("U", new KeyTableEntry(3, 6, false));
        keyMap.put("v", new KeyTableEntry(3, 7, false));
        keyMap.put("V", new KeyTableEntry(3, 7, false));
        keyMap.put("w", new KeyTableEntry(1, 1, false));
        keyMap.put("W", new KeyTableEntry(1, 1, false));
        keyMap.put("x", new KeyTableEntry(2, 7, false));
        keyMap.put("X", new KeyTableEntry(2, 7, false));
        keyMap.put("y", new KeyTableEntry(3, 1, false));
        keyMap.put("Y", new KeyTableEntry(3, 1, false));
        keyMap.put("z", new KeyTableEntry(1, 4, false));
        keyMap.put("Z", new KeyTableEntry(1, 4, false));
        keyMap.put("0", new KeyTableEntry(4, 3, false));
        keyMap.put("1", new KeyTableEntry(7, 0, false));
        keyMap.put("2", new KeyTableEntry(7, 3, false));
        keyMap.put("3", new KeyTableEntry(1, 0, false));
        keyMap.put("4", new KeyTableEntry(1, 3, false));
        keyMap.put("5", new KeyTableEntry(2, 0, false));
        keyMap.put("6", new KeyTableEntry(2, 3, false));
        keyMap.put("7", new KeyTableEntry(3, 0, false));
        keyMap.put("8", new KeyTableEntry(3, 3, false));
        keyMap.put("9", new KeyTableEntry(4, 0, false));
        keyMap.put(" ", new KeyTableEntry(7, 4, false));
        keyMap.put("SPACE", new KeyTableEntry(7, 4, false));
        keyMap.put(",", new KeyTableEntry(5, 7, false));
        keyMap.put("<", new KeyTableEntry(5, 7, true));
        keyMap.put(".", new KeyTableEntry(5, 4, false));
        keyMap.put(">", new KeyTableEntry(5, 4, true));
        keyMap.put("=", new KeyTableEntry(6, 5, false));
        keyMap.put(":", new KeyTableEntry(5, 5, false));
        keyMap.put(";", new KeyTableEntry(6, 2, false));
        keyMap.put("+", new KeyTableEntry(5, 0, false));
        keyMap.put("-", new KeyTableEntry(5, 3, false));
        keyMap.put("*", new KeyTableEntry(6, 1, false));
        keyMap.put("ASTERISK", new KeyTableEntry(6, 1, false));
        keyMap.put("/", new KeyTableEntry(6, 7, false));
        keyMap.put("@", new KeyTableEntry(5, 6, false));
        keyMap.put("#", new KeyTableEntry(1, 0, true));
        // arrow keys
        keyMap.put("ARROW_LEFT", new KeyTableEntry(7, 1, false));
        keyMap.put("ESCAPE", new KeyTableEntry(7, 1, false));
        keyMap.put("ARROW_UP", new KeyTableEntry(6, 6, false));
        // special keys
        keyMap.put("HOME", new KeyTableEntry(6, 3, false));
        keyMap.put("RUN", new KeyTableEntry(7, 7, false));
        keyMap.put("DELETE", new KeyTableEntry(0, 0, false));
        keyMap.put("BACKSPACE", new KeyTableEntry(0, 0, false));
        keyMap.put("BACK_SPACE", new KeyTableEntry(0, 0, false));
        keyMap.put("POUND", new KeyTableEntry(6, 0, false));
        // LEFT SHIFT
        keyMap.put("SHIFT", new KeyTableEntry(1, 7, false));
        keyMap.put("AUTOSHIFT", new KeyTableEntry(1, 7, false));
        // RIGHT SHIFT
        keyMap.put("CAPS_LOCK", new KeyTableEntry(6, 4, false));
        keyMap.put("COMMODORE", new KeyTableEntry(7, 5, false));
        // Break
        keyMap.put("BREAK", new KeyTableEntry(7, 7, false));
        // Enter + CTRL
        keyMap.put("\r", new KeyTableEntry(0, 1, false));
        keyMap.put("CONTROL", new KeyTableEntry(7, 2, false));
        keyMap.put("ENTER", new KeyTableEntry(0, 1, false));
        // Cursor keys
        keyMap.put("DOWN", new KeyTableEntry(0, 7, false));
        keyMap.put("UP", new KeyTableEntry(0, 7, true));
        keyMap.put("RIGHT", new KeyTableEntry(0, 2, false));
        keyMap.put("LEFT", new KeyTableEntry(0, 2, true));
        keyMap.put("CURSOR DOWN", new KeyTableEntry(0, 7, false));
        keyMap.put("CURSOR UP", new KeyTableEntry(0, 7, true));
        keyMap.put("CURSOR RIGHT", new KeyTableEntry(0, 2, false));
        keyMap.put("CURSOR LEFT", new KeyTableEntry(0, 2, true));
        // Function keys
        keyMap.put("F1", new KeyTableEntry(0, 4, false));
        keyMap.put("F3", new KeyTableEntry(0, 5, false));
        keyMap.put("F5", new KeyTableEntry(0, 6, false));
        keyMap.put("F7", new KeyTableEntry(0, 3, false));
        // some shifted keys
        keyMap.put("!", new KeyTableEntry(7, 0, true));
        keyMap.put("\"", new KeyTableEntry(7, 3, true));
        keyMap.put("§", new KeyTableEntry(1, 0, true));
        keyMap.put("$", new KeyTableEntry(1, 3, true));
        keyMap.put("%", new KeyTableEntry(2, 0, true));
        keyMap.put("&", new KeyTableEntry(2, 3, true));
        keyMap.put("'", new KeyTableEntry(3, 0, true));
        keyMap.put("(", new KeyTableEntry(3, 3, true));
        keyMap.put(")", new KeyTableEntry(4, 0, true));
        keyMap.put("?", new KeyTableEntry(6, 7, true));
        keyMap.put("[", new KeyTableEntry(5, 5, true));
        keyMap.put("]", new KeyTableEntry(6, 2, true));
    }

    /**
     * Handle a pressed key
     *
     * @param key   key that was pressed
     */
    public void keyPressed(final String key) {
        final KeyTableEntry ktEntry = (KeyTableEntry) keyMap.get(key);

        if (ktEntry != null) {
            final int row = ktEntry.row;
            final int col = ktEntry.col;

            this.rowMatrix[row] &= (0xff - (1 << col));
            this.columnMatrix[col] &= (0xff - (1 << row));

            if (ktEntry.autoshift && !this.isShiftPressed) {
                keyPressed("AUTOSHIFT");
            }

            this.lastKey = key;
            this.hasCIARead = false;
            if ("SHIFT".equals(key)) {
                this.isShiftPressed = true;
            }
        }
    }

    /**
     * Handle a released key
     *
     * @param   key key to release
     */
    public void keyReleased(final String key) {
        final KeyTableEntry ktEntry = (KeyTableEntry) keyMap.get(key);

        if (ktEntry != null) {
            if (this.hasCIARead) {
                final int row = ktEntry.row;
                final int col = ktEntry.col;

                this.rowMatrix[row] |= (1 << col);
                this.columnMatrix[col] |= (1 << row);

                if (ktEntry.autoshift && !this.isShiftPressed) {
                    keyReleased("AUTOSHIFT");
                }

                this.hasCIARead = false;
                if ("SHIFT".equals(key)) {
                    this.isShiftPressed = false;
                }

                // this key was previously pressed?
                if (key.equals(lastKey)) {
                    // this key finishes the currently typed text?
                    if ("ENTER".equals(key) || "\n".equals(key) || "\r".equals(key)) {
                        // we have a non-empty string?
                        if (this.currentText.length() > 0) {
                            // then add it as first element to the history
                            this.typedTexts.removeElement(this.currentText.toString());
                            this.typedTexts.insertElementAt(this.currentText.toString(), 0);
                            // if the history gets too long we remove the last element
                            if (this.typedTexts.size() > MAX_HISTORY) {
                                this.typedTexts.removeElementAt(MAX_HISTORY);
                            }
                            // we start with a new text to type
                            this.currentText.delete(0, this.currentText.length());
                        }
                    // backspace was pressed?
                    } else if ("BACKSPACE".equals(key) || "DELETE".equals(key)) {
                        if (this.currentText.length() > 0) {
                            this.currentText.deleteCharAt(this.currentText.length() - 1);
                        }
                    // we have a special key?
                    } else if (key.length() > 1) {
                        // this is not the shift key?
                        if (!"SHIFT".equals(key) && !"AUTOSHIFT".equals(key)) {
                            // this means we start anew with the current string
                            this.currentText.delete(0, this.currentText.length());
                        }
                    // a normal key is just added to the current string
                    } else {
                        this.currentText.append(key);
                    }
                }
            } else {
                keyTyped(key);
            }
        }
    }

    /**
     * Type a key i.e. press and release it
     *
     * @param   key key to type, may be a special key like "COMMODORE"
     */
    public void keyTyped(final String key) {
        this.typedKeysBuffer.addElement(key);
    }

    /**
     * Simulate typing a string on the keyboard
     *
     * @param   text    string to type
     */
    public void textTyped(final String text) {
        for (int i = 0; i < text.length(); ++i) {
            keyTyped(new Character(text.charAt(i)).toString());
        }
    }

    /**
     * Get a list of all texts that were typed
     *
     * @return  list of strings
     */
    public Vector getTypedTexts() {
        return this.typedTexts;
    }

    /**
     * Get read adjustment for CIA 1 register PRA or PRB
     *
     * @param   testRegisterValue   register value from PRA or PRB
     * @param   matrix  either activeColumnsMatrix or activeRowsMatrix
     * @return  read adjustment, to be AND connected to the normal register output
     */
    private short getCIARegisterAdjustment(final int testRegisterValue, final int[] matrix) {
        int result = 0xff;

        // AND all active (bit is cleared) columns/rows of the matrix
        for (int i = 0, mask = 1; i < 8; mask <<= 1, ++i) {
            if ((testRegisterValue & mask) == 0) {
                result &= matrix[i];
            }
        }

        // note that we have read the key matrix data
        this.hasCIARead = true;

        return (short) result;
    }

    /**
     * Get read adjustment for CIA 1 register PRA
     *
     * @param   testRegisterValue   register value from PRA or PRB
     * @return  read adjustment, to be AND connected to the normal register output
     */
    public short getCIAPRAAdjustment(final int testRegisterValue) {
        return getCIARegisterAdjustment(testRegisterValue, this.columnMatrix);
    }

    /**
     * Get read adjustment for CIA 1 register PRB
     *
     * @param   testRegisterValue   register value from PRA or PRB
     * @return  read adjustment, to be AND connected to the normal register output
     */
    public short getCIAPRBAdjustment(final int testRegisterValue) {
        final short result = getCIARegisterAdjustment(testRegisterValue, this.rowMatrix);

        // continue auto-typing a key if necessary
        if (this.typeDelay > 0) {
            --this.typeDelay;
        } else {
            if (this.isTyping) {
                keyReleased((String) this.typedKeysBuffer.elementAt(0));
                this.typedKeysBuffer.removeElementAt(0);
                this.isTyping = false;
                this.typeDelay = 5;
            } else if (!this.typedKeysBuffer.isEmpty()) {
                keyPressed((String) this.typedKeysBuffer.elementAt(0));
                this.isTyping = true;
                this.typeDelay = 20;
            }
        }

        return result;
    }

    public boolean hasShiftedVariant(final String key) {
        final KeyTableEntry entry = (KeyTableEntry) keyMap.get(key);

        return entry != null && !entry.autoshift && keyMap.contains(new KeyTableEntry(entry.row, entry.col, true));
    }

    // data structure for key table
    /**
     * Data structure for an entry on the key table of the C64 keyboard
     */
    class KeyTableEntry {

        /**
         * key row, column and code
         */
        public final int row,  col,  code;
        /**
         * automatically enable shift?
         */
        public final boolean autoshift;

        /**
         * Create a new KeyTableEntry
         *
         * @param   row row where the key is located
         * @param   col column where the key is located
         * @param   autoshift   automatically activate shift when the key is used?
         */
        public KeyTableEntry(final int row, final int col, final boolean autoshift) {
            this.row = row;
            this.col = col;
            this.code = (row << 3) + col;
            this.autoshift = autoshift;
        }

        public int hashCode() {
            return this.code | (this.autoshift ? (1 << 6) : 0);
        }

        public boolean equals(final Object other) {
            return other instanceof KeyTableEntry && other.hashCode() == hashCode();
        }

        public final String toString() {
            return this.getClass().getName() + "( " + this.row + ", " + this.col + ", " + this.autoshift + " )";
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.rowMatrix);
        SerializationUtils.serialize(out, this.columnMatrix);
        out.writeInt(this.typedKeysBuffer.size());
        for (int i = 0; i < this.typedKeysBuffer.size(); ++i) {
            out.writeUTF(this.typedKeysBuffer.elementAt(i).toString());
        }
        out.writeBoolean(this.isTyping);
        out.writeInt(this.typeDelay);
        out.writeUTF(this.currentText.toString());
        out.writeBoolean(this.lastKey != null);
        if (this.lastKey != null) {
            out.writeUTF(this.lastKey);
        }
        out.writeBoolean(this.hasCIARead);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.rowMatrix);
        SerializationUtils.deserialize(in, this.columnMatrix);
        this.typedKeysBuffer.removeAllElements();

        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            this.typedKeysBuffer.addElement(in.readUTF());
        }
        this.isTyping = in.readBoolean();
        this.typeDelay = in.readInt();
        if (this.currentText.length() > 0) {
            this.currentText.delete(0, this.currentText.length() - 1);
        }
        this.currentText.append(in.readUTF());
        if (in.readBoolean()) {
            this.lastKey = in.readUTF();
        }
        this.hasCIARead = in.readBoolean();
    }
}
