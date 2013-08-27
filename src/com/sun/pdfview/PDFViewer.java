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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
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
    /** the current File */
    File file;
    /** The page display */
    PagePanel page;
    /** The full screen page display, or null if not in full screen mode */
    PagePanel fsPage;
    /** the full screen window, or null if not in full screen mode */
    FullScreenWindow fullScreen;
    /** the document menu */
    JMenu docMenu;
    /** the path through the story thus far */
    Deque<Integer> storyPath = new ArrayDeque<Integer>();

    /**
     * Create a new PDFViewer 
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
        getContentPane().add(page, BorderLayout.CENTER);
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(openAction);
        file.add(quitAction);
        mb.add(file);
        JMenu view = new JMenu("View");
        view.add(nextPageAction);
        view.add(prevPageAction);
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
     * Changes the displayed page in the PDF.
     * @param pagenum the page to display
     */
    public void gotoPage(int pagenum) {
        // Fetch the page and show it in the appropriate place
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
        boolean pageShown = ((fsPage != null) ? fsPage.getPage() != null : page.getPage() != null);
        nextPageAction.setEnabled(pageShown);
        prevPageAction.setEnabled(storyPath.size() > 0);
        fullScreenAction.setEnabled(pageShown);
    }

    /**
     * Open a specific pdf file. 
     *
     * @param file the file to open
     * @throws IOException
     */
    public void openFile(File file) throws IOException {
        // First open the file for random access
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
			// Extract a file channel
			FileChannel ch = raf.getChannel();
			// Now memory-map a byte-buffer
			ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
			openPDFByteBuffer(buf, file);
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
    private void openPDFByteBuffer(ByteBuffer buf, File file) {
        // Create a PDFFile from the data
        PDFFile newfile = null;
        try {
            newfile = new PDFFile(buf);
        } catch (IOException ioe) {
            openError(file.getPath() + " doesn't appear to be a PDF file." +
                      "\n: " + ioe.getMessage ());
            return;
        }
        // Now that we're sure this document is real, close the old one.
        doClose();
        // Set up our document
        this.curFile = newfile;
        this.file = file;
        setTitle(TITLE + " (page " + getPageNumber() + ")");
        setEnabling();
        // Display the 1st page
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
    private File prevDirChoice = new File("ycnij");

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
     * 
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
        page.showPage(null);
        curFile = null;
        setTitle(TITLE);
        setEnabling();
    }

    /**
     * Shuts down.
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
     * Loads the next PDF document. If there is a text file with the same name as the 
     * current PDF file, it is assumed that it contains the different branching options.
     * The file contains space separated numbers which represent the page numbers the 
     * user can jump to next.
     * 
     * @throws IOException
     */
    public void doNextDocument() throws IOException {
    	int pageNumb = getPageNumber();
    	int nextPageNumb = pageNumb + 1;
    	// If there is a text file with the same name, load it and 
    	// let the user decide where to jump to next.
    	File branchFile = new File(file.getParentFile(), "" + pageNumb + ".txt");
    	if (branchFile.exists()) {
    		String[] pageOptions = readBranchOptions(branchFile);
    		boolean tempSwitch = false;
    		// In full screen mode we cannot display JOptionPanes (since they are windows).
    		// As a workaround we temporarily disable full screen mode.
    		if (fullScreen != null) {
    			setFullScreenMode(false);
    			tempSwitch = true;
    		}
    		String nextPage = (String)JOptionPane.showInputDialog(this, 
    				"Select which page to jump to next:", 
    				"Page Selection", 
    				JOptionPane.QUESTION_MESSAGE, 
    				null, 
    				pageOptions, 
    				pageOptions[0]);
    		if (nextPage == null) {
    			return;
    		}
    		nextPageNumb = Integer.parseInt(nextPage);
    		if (tempSwitch) {
    			setFullScreenMode(true);
    		}
    	}
    	File nextFile = new File(file.getParentFile(), "" + nextPageNumb + ".pdf");
    	if (nextFile.exists()) {
    		openFile(nextFile);
    		// Remember where we came from, so we can backtrack
    		storyPath.addFirst(pageNumb);
    		prevPageAction.setEnabled(true);
    	} else {
    		JOptionPane.showMessageDialog(this, "The end!");
    	}
    }
    
    /**
     * Reads space separated numbers from the file.
     * The numbers represent the different branching options that the user
     * has for the current page.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    private String[] readBranchOptions(File file) throws IOException {
    	FileInputStream fis = null;
    	String[] options = null;
    	try {
			fis = new FileInputStream(file);
			byte[] bytes = new byte[(int)file.length()];
			fis.read(bytes);
			String content = new String(bytes, "ASCII");
			options = content.split(" ");
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
    	return options;
    }
    
    public void doPrevDocument() throws IOException {
    	if (storyPath.size() > 0) {
	    	int prevPageNumb = storyPath.pop();
	    	File prevFile = new File(file.getParentFile(), "" + prevPageNumb + ".pdf");
	    	openFile(prevFile);
	    	if (storyPath.size() == 0) {
	    		prevPageAction.setEnabled(false);
	    	}
    	}
    }
    
    /**
     * Extracts the page number from the current file name and returns it.
     * 
     * @return page number
     */
    private int getPageNumber() {
    	int pageNumber;
    	try {
    		String filename = file.getName();
    		String nameLessExt = filename.substring(0, filename.length() - 4);
    		pageNumber = Integer.parseInt(nameLessExt);
    	} catch (Exception e) {
    		pageNumber = -1;
    	}
    	return pageNumber;
    }

    /**
     * Runs the FullScreenMode change in another thread
     */
    class PerformFullScreenMode implements Runnable {

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
            new Thread(new PerformFullScreenMode(),
                    getClass().getName() + ".setFullScreenMode").start();
        } else if (!full && fullScreen != null) {
            fullScreen.close();
            fsPage = null;
            fullScreen = null;
            gotoPage(0);
        }
    }

    public static void main(String args[]) {
        // Start the viewer
        new PDFViewer(false);
    }

    /**
     * Handle a key press for navigation
     */
    public void keyPressed(KeyEvent evt) {
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_SPACE) {
        	try {
        		doNextDocument();
			} catch (IOException e) {
				e.printStackTrace();
			}
        } else if (code == KeyEvent.VK_P) {
        	try {
        		doPrevDocument();
			} catch (IOException e) {
				e.printStackTrace();
			}
        } else if (code == KeyEvent.VK_F) {
        	doFullScreen();
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
    
    Action nextPageAction = new AbstractAction("Next page") {
        public void actionPerformed(ActionEvent evt) {
            try {
				doNextDocument();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    };
    
    Action prevPageAction = new AbstractAction("Previous page") {
        public void actionPerformed(ActionEvent evt) {
            try {
            	doPrevDocument();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    };
    
    public void keyTyped(KeyEvent evt) {
    }

    public void keyReleased(KeyEvent evt) {
    }
}
