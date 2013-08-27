/*
 * $Id: PagePanel.java,v 1.3 2009-01-26 05:09:01 tomoke Exp $
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

import javax.swing.JPanel;

/**
 * A Swing-based panel that displays a PDF page image. 
 */
@SuppressWarnings("serial")
public class PagePanel extends JPanel implements ImageObserver {

    /** The image of the rendered PDF page being displayed */
    Image currentImage;
    /** The current PDFPage that was rendered into currentImage */
    PDFPage currentPage;
    /** the current transform from device space to page space */
    AffineTransform currentXform;
    /** The horizontal offset of the image from the left edge of the panel */
    int offx;
    /** The vertical offset of the image from the top of the panel */
    int offy;
    /** the current clip, in device space */
    Rectangle2D clip;
    /** the clipping region used for the image */
    Rectangle2D prevClip;
    /** the size of the image */
    Dimension prevSize;
    /** a flag indicating whether the current page is done or not. */
    Flag flag = new Flag();

    /**
     * Create a new PagePanel, with a default size of 800 by 600 pixels.
     */
    public PagePanel() {
        setPreferredSize(new Dimension(800, 600));
        setFocusable(true);
    }

    /**
     * Stop the generation of any previous page, and draw the new one.
     * @param page the PDFPage to draw.
     */
    public synchronized void showPage(PDFPage page) {
        // stop drawing the previous page
        if (currentPage != null && prevSize != null) {
            currentPage.stop(prevSize.width, prevSize.height, prevClip);
        }

        // set up the new page
        currentPage = page;

        if (page == null) {
            // no page
            currentImage = null;
            clip = null;
            currentXform = null;
            repaint();
        } else {
            // start drawing -- clear the flag to indicate we're in progress.
            flag.clear();
            
            Dimension sz = getSize();
            if (sz.width + sz.height == 0) {
                // no image to draw.
                return;
            }
            
            // calculate the clipping rectangle in page space from the
            // desired clip in screen space.
            Rectangle2D useClip = clip;
            if (clip != null && currentXform != null) {
                useClip = currentXform.createTransformedShape(clip).getBounds2D();
            }

            Dimension pageSize = page.getUnstretchedSize(sz.width, sz.height,
                    useClip);

            // get the new image
            currentImage = page.getImage(pageSize.width, pageSize.height,
                    useClip, this);

            // calculate the transform from screen to page space
            currentXform = page.getInitialTransform(pageSize.width,
                    pageSize.height,
                    useClip);
            try {
                currentXform = currentXform.createInverse();
            } catch (NoninvertibleTransformException nte) {
                System.out.println("Error inverting page transform!");
                nte.printStackTrace();
            }
            prevClip = useClip;
            prevSize = pageSize;
            repaint();
        }
    }


    /**
     * Draw the image.
     */
    public void paint(Graphics g) {
        Dimension sz = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (currentImage == null) {
            g.setColor(Color.black);
            g.drawString("No page selected", getWidth() / 2 - 30, getHeight() / 2);
        } else {
            // draw the image
            int imwid = currentImage.getWidth(null);
            int imhgt = currentImage.getHeight(null);
            // draw it centered within the panel
            offx = (sz.width - imwid) / 2;
            offy = (sz.height - imhgt) / 2;
            if ((imwid == sz.width && imhgt <= sz.height) ||
                    (imhgt == sz.height && imwid <= sz.width)) {
                g.drawImage(currentImage, offx, offy, this);
            } else {
                // the image is bogus.  try again, or give up.
                if (currentPage != null) {
                    showPage(currentPage);
                }
                g.setColor(Color.red);
                g.drawLine(0, 0, getWidth(), getHeight());
                g.drawLine(0, getHeight(), getWidth(), 0);
            }
        }
    }

    /**
     * Gets the page currently being displayed
     */
    public PDFPage getPage() {
        return currentPage;
    }

    /**
     * Gets the size of the image currently being displayed
     */
    public Dimension getCurSize() {
        return prevSize;
    }

    /**
     * Gets the clipping rectangle in page space currently being displayed
     */
    public Rectangle2D getCurClip() {
        return prevClip;
    }

    /**
     * Waits until the page is either complete or had an error.
     */
    public void waitForCurrentPage() {
        flag.waitForFlag();
    }

    /**
     * Handles notification of the fact that some part of the image
     * changed.  Repaints that portion.
     * @return true if more updates are desired.
     */
    public boolean imageUpdate(Image img, int infoflags, int x, int y,
            int width, int height) {
        if ((infoflags & (SOMEBITS | ALLBITS)) != 0) {
            repaint(x + offx, y + offy, width, height);
        }
        if ((infoflags & (ALLBITS | ERROR | ABORT)) != 0) {
            flag.set();
            return false;
        } else {
            return true;
        }
    }

    /**
     * Set the desired clipping region (in screen coordinates), and redraw
     * the image.
     */
    public void setClip(Rectangle2D clip) {
        this.clip = clip;
        showPage(currentPage);
    }
}
