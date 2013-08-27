/*
 * $Id: PDFViewer.java,v 1.10 2009-08-07 23:18:33 tomoke Exp $
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle,
 * Santa Clara, California 95054, U.S.A. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.sun.pdfview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

/**
 * Create Your Own Adventure app based on Sun's original PDF Viewer.
 */
@SuppressWarnings("serial")
public class PDFViewer extends JFrame implements KeyListener {

    public final static String TITLE = "Create Your Own Adventure";
    /** The current PDFFile */
    PDFFile curFile;
    /** the name of the current document */
    String docName;
    /** The page display */
    PagePanel page;
    /** The full screen page display, or null if not in full screen mode */
    PagePanel fsPage;
    /** the full screen window, or null if not in full screen mode */
    FullScreenWindow fullScreen;
    /** the document menu */
    JMenu docMenu;

    /**
     * Create a new PDFViewer based on a user, with or without a thumbnail
     * panel.
     * @param useThumbs true if the thumb panel should exist, false if not.
     */
    public PDFViewer(boolean useThumbs) {
        super(TITLE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                doQuit();
            }
        });
        init();
    }

    /**
     * Initialize this PDFViewer by creating the GUI.
     */
    protected void init() {
        page = new PagePanel();
        page.addKeyListener(this);
        fullScreenAction.putValue(AbstractAction.MNEMONIC_KEY, new Integer(KeyEvent.VK_F));
        getRootPane().registerKeyboardAction(fullScreenAction, KeyStroke.getKeyStroke("F"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getContentPane().add(page, BorderLayout.CENTER);
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(openAction);
        file.add(quitAction);
        mb.add(file);
        JMenu view = new JMenu("View");
        view.add(fullScreenAction);
        mb.add(view);
        setJMenuBar(mb);
        setEnabling();
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screen.width - getWidth()) / 2;
        int y = (screen.height - getHeight()) / 2;
        setLocation(x, y);
        if (SwingUtilities.isEventDispatchThread()) {
            setVisible(true);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        setVisible(true);
                    }
                });
            } catch (InvocationTargetException ie) {
                // ignore
            } catch (InterruptedException ie) {
                // ignore
            }
        }
    }

    /**
     * Changes the displayed page.
     * @param pagenum the page to display
     */
    public void gotoPage(int pagenum) {
        // fetch the page and show it in the appropriate place
        PDFPage pg = curFile.getPage(pagenum + 1);
        if (fsPage != null) {
            fsPage.showPage(pg);
            fsPage.requestFocus();
        } else {
            page.showPage(pg);
            page.requestFocus();
        }
        setEnabling();
    }

    /**
     * Enable or disable all of the actions based on the current state.
     */
    public void setEnabling() {
        boolean pageshown = ((fsPage != null) ? fsPage.getPage() != null : page.getPage() != null);
        fullScreenAction.setEnabled(pageshown);
    }

    /**
     * Open a specific pdf file. 
     *
     * @param file the file to open
     * @throws IOException
     */
    public void openFile(File file) throws IOException {
        // first open the file for random access
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
			// extract a file channel
			FileChannel ch = raf.getChannel();
			// now memory-map a byte-buffer
			ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
			openPDFByteBuffer(buf, file.getPath(), file.getName());
		} finally {
			raf.close();
		}
    }

    /**
     * Open the ByteBuffer data as a PDFFile and start to process it.
     *
     * @param buf
     * @param path
     */
    private void openPDFByteBuffer(ByteBuffer buf, String path, String name) {
        // create a PDFFile from the data
        PDFFile newfile = null;
        try {
            newfile = new PDFFile(buf);
        } catch (IOException ioe) {
            openError(path + " doesn't appear to be a PDF file." +
                      "\n: " + ioe.getMessage ());
            return;
        }
        // Now that we're sure this document is real, close the old one.
        doClose();
        // set up our document
        this.curFile = newfile;
        docName = name;
        setTitle(TITLE + ": " + docName);
        setEnabling();
        // display page 1.
        gotoPage(0);
    }

    /**
     * Display a dialog indicating an error.
     */
    public void openError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error opening file",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * A file filter for PDF files.
     */
    FileFilter pdfFilter = new FileFilter() {

        public boolean accept(File f) {
            return f.isDirectory() || f.getName().endsWith(".pdf");
        }

        public String getDescription() {
            return "Choose a PDF file";
        }
    };
    private File prevDirChoice;

    /**
     * Ask the user for a PDF file to open from the local file system
     */
    public void doOpen() {
        try {
            JFileChooser fc = new JFileChooser();
            fc.setCurrentDirectory(prevDirChoice);
            fc.setFileFilter(pdfFilter);
            fc.setMultiSelectionEnabled(false);
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                try {
                    prevDirChoice = fc.getSelectedFile();
                    openFile(fc.getSelectedFile());
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open file.\n",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Open a local file, given a string filename
     * @param name the name of the file to open
     */
    public void doOpen(String name) {
        try {
		    openFile(new File(name));
		} catch (IOException ex) {
		    Logger.getLogger(PDFViewer.class.getName()).log(Level.SEVERE, null, ex);
		}
    }
    
    /**
     * Close the current document.
     */
    public void doClose() {
        setFullScreenMode(false);
        page.showPage(null);
        curFile = null;
        setTitle(TITLE);
        setEnabling();
    }

    /**
     * Shuts down all known threads.  This ought to cause the JVM to quit
     * if the PDFViewer is the only application running.
     */
    public void doQuit() {
        doClose();
        dispose();
        System.exit(0);
    }

    /**
     * Enter full screen mode
     */
    public void doFullScreen() {
        setFullScreenMode(fullScreen == null);
    }

    /**
     * Runs the FullScreenMode change in another thread
     */
    class PerformFullScreenMode implements Runnable {

        boolean force;

        public PerformFullScreenMode(boolean forcechoice) {
            force = forcechoice;
        }

        public void run() {
            fsPage = new PagePanel();
            fsPage.setBackground(Color.black);
            page.showPage(null);
            fullScreen = new FullScreenWindow(fsPage);
            fsPage.addKeyListener(PDFViewer.this);
            gotoPage(0);
            fullScreenAction.setEnabled(true);
        }
    }

    /**
     * Starts or ends full screen mode.
     * @param full true to enter full screen mode, false to leave
     * to use the second time full screen mode is entered.
     */
    public void setFullScreenMode(boolean full) {
        if (full && fullScreen == null) {
            fullScreenAction.setEnabled(false);
            new Thread(new PerformFullScreenMode(false),
                    getClass().getName() + ".setFullScreenMode").start();
        } else if (!full && fullScreen != null) {
            fullScreen.close();
            fsPage = null;
            fullScreen = null;
            gotoPage(0);
        }
    }

    public static void main(String args[]) {
        // start the viewer
        new PDFViewer(false);
    }

    /**
     * Handle a key press for navigation
     */
    public void keyPressed(KeyEvent evt) {
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_SPACE) {
            //doNext();
        } else if (code == KeyEvent.VK_ESCAPE) {
            setFullScreenMode(false);
        }
    }
    
    Action openAction = new AbstractAction("Open...") {
        public void actionPerformed(ActionEvent evt) {
            doOpen();
        }
    };
    
    Action quitAction = new AbstractAction("Quit") {
        public void actionPerformed(ActionEvent evt) {
            doQuit();
        }
    };
    
    Action fullScreenAction = new AbstractAction("Full screen") {
        public void actionPerformed(ActionEvent evt) {
            doFullScreen();
        }
    };
    
    public void keyTyped(KeyEvent evt) {
    }

    public void keyReleased(KeyEvent evt) {
    }
}
