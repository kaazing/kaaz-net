/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.netx.ws.internal.io;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public class WsReader extends Reader {
    private final HttpURLConnection connection;
    private final InputStream in;
    private final WsOutputStream out;
    private final byte[] header;

    private int headerOffset;
    private int payloadOffset;
    private long payloadLength;
    private int charBytes;
    private int remaining;

    public WsReader(HttpURLConnection connection, WsOutputStream out) throws IOException {
        if (connection == null) {
            throw new NullPointerException("Null HttpURLConnection passed in");
        }

        if (out == null) {
            throw new NullPointerException("Null WsOutputStream passed in");
        }

        this.connection = connection;
        this.in = connection.getInputStream();
        this.out = out;
        this.header = new byte[10];
        this.payloadOffset = -1;
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            throw new IndexOutOfBoundsException();
        }

        int mark = offset;

        // This loop will be entered only if the entire WebSocket frame has been drained. If there was fragmentation at the TCP
        // level, this method will be invoked again with different offset and length. At that time, we will just use the
        // payloadLength and payloadOffset values that were from the previous attempt(s) to read.
        while (payloadLength == 0) {
            while (payloadOffset == -1) {
                int headerByte = in.read();
                if (headerByte == -1) {
                    return -1;
                }
                header[headerOffset++] = (byte) headerByte;
                switch (headerOffset) {
                case 1:
                    int flags = (header[0] & 0xF0) >> 4;
                    switch (flags) {
                    case 0:
                    case 8:
                        break;
                    default:
                        out.writeClose(1002, null);
                        in.close();
                        throw new IOException(format("Protocol Violation: Reserved bits set %02X", flags));
                    }

                    int opcode = header[0] & 0x0F;
                    switch (opcode) {
                    case 0x00:
                    case 0x01:
                    case 0x08:
                    case 0x09:
                    case 0x0A:
                        break;
                    default:
                        out.writeClose(1002, null);
                        connection.disconnect();
                        throw new IOException(format("Non-text frame - opcode = %d", opcode));
                    }
                    break;
                case 2:
                    boolean masked = (header[1] & 0x80) != 0x00;
                    if (masked) {
                        out.writeClose(1002, null);
                        connection.disconnect();
                        throw new IOException("Masked server-to-client frame");
                    }
                    switch (header[1] & 0x7f) {
                    case 126:
                    case 127:
                        break;
                    default:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    }
                    break;
                case 4:
                    switch (header[1] & 0x7f) {
                    case 126:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    default:
                        break;
                    }
                    break;
                case 10:
                    switch (header[1] & 0x7f) {
                    case 127:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    default:
                        break;
                    }
                    break;
                }
            }

            // If the current frame is either CLOSE, PING, or PONG, then we just filter out it's bytes.
            filterControlFrames();
            if ((header[0] & 0x0F) == 0x08) {
                return -1;
            }

            // If the payload length is zero, then we should start reading the new frame.
            if (payloadLength == 0) {
                payloadOffset = -1;
                headerOffset = 0;
            }
        }

        // payloadOffset and payloadLength are in terms of bytes. However, off and len are in terms of chars. The payload can
        // include multi-byte UTF-8 characters. The multi-byte characters can span across WebSocket frames or TCP fragments. If
        // there was fragmentation at the TCP layer before the entire frame was drained, this loop should be executed to drain
        // the payload.
        while (payloadOffset < payloadLength) {
            int b = -1;

            while ((payloadOffset < payloadLength) && (remaining > 0)) {
                // Read the remaining bytes of a multi-byte character. These bytes could be in two successive WebSocket frames.
                b = in.read();
                if (b == -1) {
                    return offset - mark;
                }

                payloadOffset++;

                switch (remaining) {
                case 3:
                case 2:
                    charBytes = (charBytes << 6) | (b & 0x3F);
                    remaining--;
                    break;
                case 1:
                    cbuf[offset++] = (char) ((charBytes << 6) | (b & 0x3F));
                    remaining--;
                    charBytes = 0;
                    break;
                case 0:
                    break;
                }
            }

            b = in.read();
            if (b == -1) {
                break;
            }

            payloadOffset++;

            // Check if the byte read is the first of a multi-byte character.
            remaining = Utf8Util.remainingUTF8Bytes(b);

            switch (remaining) {
            case 0:
                // ASCII char.
                cbuf[offset++] = (char) (b & 0x7F);
                break;
            case 1:
                charBytes = b & 0x1F;
                break;
            case 2:
                charBytes = b & 0x0F;
                break;
            case 3:
                charBytes = b & 0x07;
                break;
            default:
                throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
            }
        }

        if (payloadOffset == payloadLength) {
            headerOffset = 0;
            payloadOffset = -1;
            payloadLength = 0;
        }

        // Return the number of chars read.
        return offset - mark;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private void filterControlFrames() throws IOException {
        int opcode = header[0] & 0x0F;

        if ((opcode == 0x00) || (opcode == 0x01)) {
            return;
        }

        switch (opcode) {
        case 0x08:
            int code = 0;
            byte[] reason = null;

            if (payloadLength >= 2) {
                // Read the first two bytes as the CLOSE code.
                int b1 = in.read();
                int b2 = in.read();

                code = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                if ((code == 1005) || (code == 1006) || (code == 1015)) {
                    code = 1002;
                }

                // If reason is also received, then just drain those bytes.
                if (payloadLength > 2) {
                    reason = new byte[(int) (payloadLength - 2)];
                    int bytesRead = in.read(reason);

                    if (bytesRead == -1) {
                        throw new IOException("End of stream");
                    }

                    if ((reason.length > 123) || !Utf8Util.isValidUTF8(reason)) {
                        code = 1002;
                    }

                    if (code != 1000) {
                        reason = null;
                    }
                }
            }

            if (out.wasCloseSent()) {
                // If the client had earlier initiated a CLOSE and this is server's response as part of the CLOSE handshake,
                // then we should close the connection.
                connection.disconnect();
            }
            else {
                // The server has initiated a CLOSE. The client should reflect the CLOSE including the code(if any) to
                // complete the CLOSE handshake and then close the connection.
                out.writeClose(code, reason);
                connection.disconnect();
            }
            break;

        case 0x09:
            byte[] buf = null;
            if (payloadLength > 0) {
                buf = new byte[(int) payloadLength];
                int bytesRead = in.read(buf);

                if (bytesRead == -1) {
                    throw new IOException("End of stream");
                }
            }

            if ((buf != null) && (buf.length > 125)) {
                out.writeClose(1002, null);
                connection.disconnect();

                // ### TODO: Do we need to do this?
                throw new IOException("Protocol Violation: PING payload is more than 125 bytes");
            }
            else {
                if (opcode == 0x09) {
                    // Send the PONG frame out with the same payload that was received with PING.
                    out.writePong(buf);
                }
            }
            break;

        case 0x0A:
            int closeCode = 0;
            if (payloadLength > 125) {
                closeCode = 1002;
            }
            out.writeClose(closeCode, null);
            connection.disconnect();

            // ### TODO: Do we need to do this?
            throw new IOException("Protocol Violation: Received unexpected PONG");

        default:
            throw new IOException(format("Protocol Violation: Unrecognized opcode %d", opcode));
        }

        // Get ready to read the next frame after CLOSE frame is sent out.
        payloadLength = 0;
        payloadOffset = -1;
        headerOffset = 0;
    }

    private static long payloadLength(byte[] header) {
        int length = header[1] & 0x7f;
        switch (length) {
        case 126:
            return (header[2] & 0xff) << 8 | (header[3] & 0xff);
        case 127:
            return (header[2] & 0xff) << 56 |
                   (header[3] & 0xff) << 48 |
                   (header[4] & 0xff) << 40 |
                   (header[5] & 0xff) << 32 |
                   (header[6] & 0xff) << 24 |
                   (header[7] & 0xff) << 16 |
                   (header[8] & 0xff) << 8  |
                   (header[9] & 0xff);
        default:
            return length;
        }
    }
}
