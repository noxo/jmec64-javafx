/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C1541;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * class for the 1541 command channel
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class DriveCommandChannel extends ByteArrayDriveChannel {

    /**
     * Result string if everything is OK
     */
    public final static String OK = "00,OK,00,00";
    /**
     * Result string if files were deleted
     */
    public final static String FILES_SCRATCHED = "01,FILES SCRATCHED,01,00";
    /**
     * Result string if we could not execute a write operation
     */
    public final static String WRITE_ERROR = "25,WRITE ERROR,00,00";
    /**
     * Write operation cannot be executed as the disk is write protected
     */
    public final static String WRITE_PROTECT_ON_ERROR = "26,WRITE PROTECT ON,00,00";
    /**
     * Result string if we don't understand the syntax of a command
     */
    public final static String SYNTAX_ERROR = "30,SYNTAX ERROR,00,00";
    /**
     * Result string if a file was not found
     */
    public final static String FILE_NOT_FOUND_ERROR = "62,FILE NOT FOUND,00,00";
    /**
     * Result string if a file to create/rename already exists
     */
    public final static String FILE_EXISTS_ERROR = "63,FILE EXISTS,00,00";
    /**
     * Result string if it was attempted to allocate an already allocated block
     */
    public final static String NO_BLOCK_ERROR = "65,NO_BLOCK";
    /**
     * Initialization string
     */
    public final static String INIT_MESSAGE = "73,CBM DOS V2.6 1541,00,00";
    /**
     * the drive channels 0-15
     */
    private final DriveChannel[] channels;
    /**
     * the last message we sent
     */
    private String lastMessage;

    /**
     * Create a new command channel
     * 
     * @param   c1541   c1541 where this channel is attached to
     * @param   channels    the drive channels 0-15
     */
    public DriveCommandChannel(final C1541 c1541, final DriveChannel[] channels) {
        super(c1541);
        this.channels = channels;
        setMessage(INIT_MESSAGE);
    }

    /**
     * Set the current message
     * 
     * @param   message message to set
     */
    protected final void setMessage(final String message) {
        this.in = new ByteArrayInputStream(message.getBytes());
        this.lastMessage = message;
    }

    /**
     * Execute the command
     * 
     * @throws  IOException if the command cannot be properly committed
     */
    public void commit() throws IOException {
        // evaluate the command sent
        final String command = this.out.toString();

        if (command.length() == 0) {
            // we have no command, that's OK
            setMessage(OK);
        } else {
            // interpret the command
            switch (command.charAt(0)) {
                case 'I':
                    // initialize command: we reset the c1541 but leave the file entries
                    this.c1541.initialize();
                    setMessage(OK);
                    break;

                case 'C': {
                    // copy disk file
                    final int from = command.indexOf(':') + 1,  to = command.indexOf('=');
                    final String newFileName = command.substring(from, to);
                    final int from2 = to + 1,  to2 = command.length() - 1;
                    final String oldFileName = command.substring(from2, to2);
                    final DriveHandler handler = this.c1541.getDriveHandler();

                    final byte[] bytes = handler.readFile(oldFileName);

                    handler.writeFile(newFileName, bytes);
                    break;
                }
                case 'V':
                    // validate the disk: for disks we do a validation, otherwise we do nothing and just report that everything was OK
                    if (this.c1541.getDriveHandler() instanceof D64DriveHandler) {
                        ((D64DriveHandler) this.c1541.getDriveHandler()).validate();
                    }
                    setMessage(OK);
                    break;

                case 'N': {
                    // format disk: for disks we do the formatting, otherwise we pretent we had a write protection activated
                    if (this.c1541.getDriveHandler() instanceof D64DriveHandler) {
                        final int from = command.indexOf(':') + 1,  to = command.indexOf(',') < 0 ? command.length() - 1 : command.indexOf(',');
                        final String fileName = command.substring(from, to);
                        int id = -1;

                        try {
                            final int from2 = to + 1,  to2 = command.length() - 1;

                            id = Integer.parseInt(command.substring(from2, to2));
                        } catch (Exception e) {
                        // no valid ID was specified
                        }

                        ((D64DriveHandler) this.c1541.getDriveHandler()).format(fileName, id);
                        setMessage(OK);
                    } else {
                        setMessage(WRITE_PROTECT_ON_ERROR);
                    }
                    break;
                }
                case 'S': {
                    // scratch = delete file
                    final int from = command.indexOf(':') + 1,  to = command.length() - 1;
                    final String filename = command.substring(from, to);

                    this.c1541.getDriveHandler().deleteFile(filename);
                    setMessage(FILES_SCRATCHED);
                    break;
                }

                case 'R': {
                    // rename a file
                    final int from = command.indexOf(':') + 1,  to = command.indexOf('=');
                    final String newFileName = command.substring(from, to);
                    final int from2 = to + 1,  to2 = command.length() - 1;
                    final String oldFileName = command.substring(from2, to2);

                    try {
                        this.c1541.getDriveHandler().renameFile(newFileName, oldFileName);
                        setMessage(OK);
                    } catch (IOException e) {
                        setMessage(FILE_EXISTS_ERROR);
                    }
                    break;
                }

                case 'B':
                    // block/buffer commands, only supported on disks
                    if (this.c1541.getDriveHandler() instanceof D64DriveHandler) {
                        executeBlockCommand(command);
                    } else {
                        setMessage(SYNTAX_ERROR);
                    }
                    break;

                case 'U':
                    // we only support the U1 and U2 "User" command and only on disks
                    if (this.c1541.getDriveHandler() instanceof D64DriveHandler) {
                        if (command.startsWith("U1:")) {
                            executeReadBlockCommand(command.substring(3), true);
                        } else if (command.startsWith("U2:")) {
                            executeWriteBlockCommand(command.substring(3), true);
                        } else {
                            setMessage(SYNTAX_ERROR);
                        }
                    }
                    break;

                case 'M':
                // memory commands: we report a syntax error
                default:
                    // an unknown command results in a syntax error
                    setMessage(SYNTAX_ERROR);
            }
        }

        this.out.reset();

        if (C1541.DEBUG) {
            System.out.println("Floppy-Command '" + command + "' led to result '" + this.lastMessage + "'.\n");
        }
    }

    /**
     * Execute a block command
     *
     * @param   command holds the complete command
     */
    private void executeBlockCommand(final String command) {
        if (command.length() < 4 || command.charAt(1) != '-' || command.charAt(3) != ':') {
            setMessage(SYNTAX_ERROR);
        } else {
            try {
                switch (command.charAt(2)) {
                    case 'R': {
                        // block read
                        final String parameters = command.substring(4);

                        executeReadBlockCommand(parameters, false);
                        break;
                    }

                    case 'W': {
                        // block write
                        final String parameters = command.substring(4);

                        executeWriteBlockCommand(parameters, false);
                        break;
                    }

                    case 'P': {
                        // buffer pointer
                        int[] params = parseBlockCommandParameters(command.substring(4), 2);
                        final int channelNo = params[ 0];
                        final int skip = params[ 1];
                        final ByteArrayDriveChannel channel = (ByteArrayDriveChannel) this.channels[channelNo];
                        // we skip the desired number of bytes...
                        final byte[] skipped = channel.skip(skip);

                        // ...and write these to the output. This is useful if data is written to the block.
                        channel.write(skipped, 0, skipped.length);

                        // set channel to block mode if we have a MultiPurposeDriveChannel
                        if (channel instanceof MultiPurposeDriveChannel) {
                            ((MultiPurposeDriveChannel) channel).setBlockMode(true);
                        }
                        break;
                    }

                    case 'A': {
                        // block allocate
                        int[] params = parseBlockCommandParameters(command.substring(4), 3);
                        final int track = params[ 1];
                        final int sector = params[ 2];
                        final D64DriveHandler diskHandler = (D64DriveHandler) this.c1541.getDriveHandler();

                        diskHandler.allocateBlock(track, sector);
                        break;
                    }

                    case 'F': {
                        // block free
                        int[] params = parseBlockCommandParameters(command.substring(4), 3);
                        final int track = params[ 1];
                        final int sector = params[ 2];
                        final D64DriveHandler diskHandler = (D64DriveHandler) this.c1541.getDriveHandler();

                        diskHandler.freeBlock(track, sector);
                        break;
                    }

                    case 'E':
                    // block execute
                    default:
                        // in all cases we do not understand or support, we report a syntax error
                        setMessage(SYNTAX_ERROR);
                }
            } catch (Exception e) {
                // we encountered some kind of error while processing the command, most likely a syntax error
                setMessage(SYNTAX_ERROR);
            }
        }
    }

    /**
     * Execute a read block command
     *
     * @param   parameters  command parameters
     */
    private void executeReadBlockCommand(final String parameters, final boolean readFullBlock) throws IOException {
        final int[] params = parseBlockCommandParameters(parameters, 4);
        final int channelNo = params[ 0];
        final int track = params[ 2];
        final int sector = params[ 3];
        final D64DriveHandler diskHandler = (D64DriveHandler) this.c1541.getDriveHandler();
        final DriveChannel channel = this.channels[channelNo];

        diskHandler.gotoBlock(track, sector);

        final byte[] blockBytes = diskHandler.readBlock();

        if (readFullBlock) {
            ((ByteArrayDriveChannel) channel).fill(blockBytes, 0, D64DriveHandler.BYTES_PER_SECTOR);
        } else {
            final int numBytes = blockBytes[ 0];

            ((ByteArrayDriveChannel) channel).fill(blockBytes, 1, numBytes);
        }

        // set channel to block mode if we have a MultiPurposeDriveChannel
        if (channel instanceof MultiPurposeDriveChannel) {
            ((MultiPurposeDriveChannel) channel).setBlockMode(true);
        }

        setMessage(OK);
    }

    /**
     * Execute a write block command
     *
     * @param   parameters  command parameters
     */
    private void executeWriteBlockCommand(final String parameters, final boolean writeFullBlock) throws IOException {
        final int[] params = parseBlockCommandParameters(parameters, 4);
        final int channelNo = params[ 0];
        final int track = params[ 2];
        final int sector = params[ 3];
        final D64DriveHandler diskHandler = (D64DriveHandler) this.c1541.getDriveHandler();
        final ByteArrayDriveChannel channel = (ByteArrayDriveChannel) this.channels[channelNo];

        diskHandler.gotoBlock(track, sector);

        final byte[] blockBytes = diskHandler.readBlock();

        if (writeFullBlock) {
            System.arraycopy(channel.out.toByteArray(), 0, blockBytes, 0, D64DriveHandler.BYTES_PER_SECTOR);
        } else {
            blockBytes[ 0] = (byte) channel.out.size();

            System.arraycopy(channel.out.toByteArray(), 0, blockBytes, 1, Math.min(D64DriveHandler.BYTES_PER_SECTOR - 1, channel.out.size()));
        }

        diskHandler.writeBlock(blockBytes);

        channel.out.reset();

        setMessage(OK);
    }

    /**
     * Get the parameters for a block command
     *
     * @param   parameters  part of the command string holding the parameters
     * @param   count   maximum number of parameters to read
     * @return  array with command parameter values
     */
    private int[] parseBlockCommandParameters(final String parameters, final int count) {
        final int[] result = new int[count];
        StringBuffer param = new StringBuffer();

        for (int i = 0,  n = 0; n < count && i < parameters.length(); ++i) {
            final char c = parameters.charAt(i);

            // TODO: the following parser is a bit too lenient, it might be necessary to be stricter
            // we have a digit for the current parameter?
            if (c >= '0' && c <= '9') {
                param.append(c);
            } else {
                // this was a parameter delimiter
                if (param.length() > 0) {
                    // then process the current parameter...
                    result[n++] = Integer.parseInt(param.toString());
                    // ...and prepare for the next one
                    param = new StringBuffer();
                }
            }
        }

        return result;
    }

    /**
     * We never close this channel but keep the last message, but all other this.c1541.getDriveChannels() of this device are closed
     * 
     * @throws  IOException if the channel cannot be closed
     */
    public void close() throws IOException {
        // keep the last message
        setMessage(this.lastMessage);
        // close other this.c1541.getDriveChannels()
        for (int i = 0; i < this.channels.length - 1; ++i) {
            if (null != this.channels[i]) {
                this.channels[i].close();
            }
        }
    }
}
