/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import com.android.ddmlib.RawImage;

public class AndroidProjector {
    private Label mImageLabel;
    private RawImage mRawImage;
    private boolean mRotateImage = false;

    private final static String ADB_HOST = "127.0.0.1";
    private final static int ADB_PORT = 5037;
    private final static int WAIT_TIME = 5;  // ms

    private void open() throws IOException {
        Display.setAppName("Android Projector");
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Device Screen");
        createContents(shell);
        shell.open();

        SocketChannel adbChannel = null;
        try {
            while (!shell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    adbChannel = connectAdbDevice();
                    if (adbChannel == null)
                        break;

                    if (startFramebufferRequest(adbChannel)) {
                        getFramebufferData(adbChannel);
                        updateDeviceImage(shell, mRotateImage ? mRawImage.getRotated() : mRawImage);
                    }
                    adbChannel.close();
                }
            }
        } finally {
            if (adbChannel != null)
                adbChannel.close();
            display.dispose();
        }
    }

    private void createContents(Shell shell) {
        Menu menuBar = new Menu(shell, SWT.BAR);
        MenuItem viewItem = new MenuItem(menuBar, SWT.CASCADE);
        viewItem.setText("&View");
        Menu viewMenu = new Menu(menuBar);
        viewItem.setMenu(viewMenu);

        final MenuItem portraitItem = new MenuItem(viewMenu, SWT.RADIO);
        final MenuItem landscapeItem = new MenuItem(viewMenu, SWT.RADIO);

        portraitItem.setText("Portrait");
        portraitItem.setSelection(true);
        portraitItem.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                mRotateImage = false;
            }
        });

        landscapeItem.setText("Landscape");
        landscapeItem.setSelection(false);
        landscapeItem.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                mRotateImage = true;
            }
        });

        shell.setMenuBar(menuBar);
        shell.setLayout(new FillLayout());
        mImageLabel = new Label(shell, SWT.BORDER);
        mImageLabel.pack();
        shell.pack();
    }

    private SocketChannel connectAdbDevice() throws IOException {
        InetAddress hostAddr;
        try {
            hostAddr = InetAddress.getByName(ADB_HOST);
        } catch (UnknownHostException e) {
            return null;
        }

        InetSocketAddress socketAddr = new InetSocketAddress(hostAddr, ADB_PORT);
        SocketChannel adbChannel = SocketChannel.open(socketAddr);
        adbChannel.configureBlocking(false);

        // Select first USB device.
        sendAdbRequest(adbChannel, "host:transport-usb");
        if (!checkAdbResponse(adbChannel))
            return null;

        return adbChannel;
    }

    private boolean startFramebufferRequest(SocketChannel adbChannel) throws IOException {
        // Request device framebuffer.
        sendAdbRequest(adbChannel, "framebuffer:");
        if (checkAdbResponse(adbChannel)) {
            getFramebufferHeader(adbChannel);
            return true;
        }

        return false;
    }

    private void getFramebufferHeader(SocketChannel adbChannel) throws IOException {
        // Get protocol version.
        ByteBuffer buf = ByteBuffer.wrap(new byte[4]);
        readAdbChannel(adbChannel, buf);
        buf.rewind();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int version = buf.getInt();
        int headerSize = RawImage.getHeaderSize(version);

        // Get header.
        buf = ByteBuffer.wrap(new byte[headerSize * 4]);
        readAdbChannel(adbChannel, buf);
        buf.rewind();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        mRawImage = new RawImage();
        mRawImage.readHeader(version, buf);
    }
    
    private void getFramebufferData(SocketChannel adbChannel) throws IOException {
        // Send nudge.
        byte[] nudge = { 0 };
        ByteBuffer buf = ByteBuffer.wrap(nudge);
        writeAdbChannel(adbChannel, buf);

        // Receive framebuffer data.
        byte[] data = new byte[mRawImage.size];
        buf = ByteBuffer.wrap(data);
        readAdbChannel(adbChannel, buf);
        mRawImage.data = data;
    }

    private void sendAdbRequest(SocketChannel adbChannel, String request) throws IOException {
        String requestStr = String.format("%04X%s", request.length(), request);
        ByteBuffer buf = ByteBuffer.wrap(requestStr.getBytes());
        writeAdbChannel(adbChannel, buf);
    }

    private boolean checkAdbResponse(SocketChannel adbChannel) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(new byte[4]);
        readAdbChannel(adbChannel, buf);
        return buf.array()[0] == (byte)'O' && buf.array()[3] == (byte)'Y';
    }

    private void writeAdbChannel(SocketChannel adbChannel, ByteBuffer buf) throws IOException {
        while (buf.position() != buf.limit()) {
            int count = adbChannel.write(buf);
            if (count < 0) {
                throw new IOException("EOF");
            } else if (count == 0) {
                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private void readAdbChannel(SocketChannel adbChannel, ByteBuffer buf) throws IOException {
        while (buf.position() != buf.limit()) {
            int count = adbChannel.read(buf);
            if (count < 0) {
                throw new IOException("EOF");
            } else if (count == 0) {
                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private void updateDeviceImage(Shell shell, RawImage rawImage) {
        PaletteData paletteData = new PaletteData(
                rawImage.getRedMask(),
                rawImage.getGreenMask(),
                rawImage.getBlueMask());
        ImageData imageData = new ImageData(
                rawImage.width,
                rawImage.height,
                rawImage.bpp,
                paletteData,
                1,
                rawImage.data);
        Image image = new Image(shell.getDisplay(), imageData);
        mImageLabel.setImage(image);
        mImageLabel.pack();
        shell.pack();
    }

    public static void main(String[] args) {
        AndroidProjector androidProjector = new AndroidProjector();
        try {
            androidProjector.open();
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
