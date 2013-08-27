/*
 * $Id: FullScreenWindow.java,v 1.4 2009-08-07 23:18:33 tomoke Exp $
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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * A window that takes over the full screen.  You can put exactly one
 * JComponent into the window.  If there are multiple screens attached
 * to the computer, this class will display buttons on each screen so
 * that the user can select which one receives the full-screen window.
 */
public class FullScreenWindow {
   
	/** The current screen for the FullScreenWindow */
    private GraphicsDevice screen;

    /** The JFrame filling the screen */
    private JFrame frame;

    /**
     * Create a full screen window containing a JComponent.  The user
     * will only be asked which screen to display on if there are multiple
     * monitors attached and the user hasn't already made a choice.
     * @param part the JComponent to display
     */
    public FullScreenWindow(JComponent part) {
		init(part);
    }

    /**
     * Close the full screen window.  This particular FullScreenWindow
     * object cannot be used again.
     */
    public void close() {
		screen.setFullScreenWindow(null);
		if (frame != null) {
		    frame.dispose();
		}
    }

    /**
     * Create the window.
     * @param part the JComponent to display
     */
    private void init(JComponent part) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		screen = ge.getScreenDevices()[0];
		GraphicsConfiguration gc= screen.getDefaultConfiguration();
		frame = new JFrame(gc);
		frame.setUndecorated(true);
		frame.setBounds(gc.getBounds());
		frame.getContentPane().add(part);
		frame.setVisible(true);
		screen.setFullScreenWindow(frame);
    }
 
}
