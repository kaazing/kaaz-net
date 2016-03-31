/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
package org.kaazing.netx.ws.specification;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.ws.MessageType.BINARY;
import static org.kaazing.netx.ws.MessageType.TEXT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WsURLConnection;

/**
 * RFC-6455, section 5.4 "Fragmentation"
 */
public class FragmentationIT {
    private final Random random = new Random();

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/fragmentation");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.echo.text.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSendTextFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(125).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "client.echo.binary.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSendBinaryFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[125];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[125];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }

        k3po.finish();
        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
    "client.echo.binary.payload.length.125.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSentBinaryFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(125);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        byte[] binaryMessage = new byte[125];
        byte[] binaryFrame = new byte[25];
        int fragmentCount = 5;
        int messageOffset = 0;
        OutputStream binaryOutputStream = messageWriter.getOutputStream();

        // Stream out a binary message that spans across multiple WebSocket frames.
        while (fragmentCount > 0) {
            fragmentCount--;

            random.nextBytes(binaryFrame);
            System.arraycopy(binaryFrame, 0, binaryMessage, messageOffset, binaryFrame.length);
            messageOffset += binaryFrame.length;

            binaryOutputStream.write(binaryFrame);

            if (fragmentCount > 0) {
                // Send the CONTINUATION frame.
                binaryOutputStream.flush();
            }
            else {
                // Close the stream to indicate the end of the message.
                binaryOutputStream.close();
            }
        }

        byte[] recvdBinaryMessage = new byte[125];
        MessageType type = null;
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert !messageReader.streaming();

            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(recvdBinaryMessage);
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        assertArrayEquals(binaryMessage, recvdBinaryMessage);
        k3po.finish();
    }

    @Test
    @Specification({
        "client.echo.text.payload.length.125.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSentTextFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(125);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        char[] textMessage = new char[125];
        char[] textFrame;
        int fragmentCount = 5;
        int messageOffset = 0;
        Writer textWriter = messageWriter.getWriter();

        // Stream out a text message that spans across multiple WebSocket frames.
        while (fragmentCount > 0) {
            fragmentCount--;

            String frame = new RandomString(25).nextString();
            textFrame = frame.toCharArray();
            System.arraycopy(textFrame, 0, textMessage, messageOffset, textFrame.length);
            messageOffset += textFrame.length;

            textWriter.write(textFrame);

            if (fragmentCount > 0) {
                // Send the CONTINUATION frame.
                textWriter.flush();
            }
            else {
                // Close the writer to indicate the end of the message.
                textWriter.close();
            }
        }

        char[] recvdTextMessage = new char[125];
        MessageType type = null;
        int charsRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert !messageReader.streaming();

            switch (type) {
            case TEXT:
                charsRead = messageReader.readFully(recvdTextMessage);
                assertEquals(125, charsRead);
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        assertArrayEquals(textMessage, recvdTextMessage);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithEmptyPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                while (count != -1) {
                    count = in.read(readBytes);
                    if (count != -1) {
                        bytesRead += count;
                    }
                }
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.0.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithEmptyPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                while (count != -1) {
                    count = in.read(readBytes);
                    if (count != -1) {
                        bytesRead += count;
                    }
                }
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        byte[] readBytes = new byte[125];
        MessageType type = null;
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                int offset = 0;
                while ((count != -1) && (offset < readBytes.length)) {
                    count = in.read(readBytes, offset, readBytes.length - offset);
                    if (count != -1) {
                        bytesRead += count;
                        offset += count;
                    }
                }
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                int offset = 0;
                while ((count != -1) && (offset < readBytes.length)) {
                    count = in.read(readBytes, offset, readBytes.length - offset);
                    if (count != -1) {
                        bytesRead += count;
                        offset += count;
                    }
                }
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented.with.some.empty.fragments/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmentedWithSomeEmptyFragments() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                int offset = 0;
                while ((count != -1) && (offset < readBytes.length)) {
                    count = in.read(readBytes, offset, readBytes.length - offset);
                    if (count != -1) {
                        bytesRead += count;
                        offset += count;
                    }
                }
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoServerSendBinaryFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert !messageReader.streaming();

            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(readBytes);
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        messageWriter.writeFully(readBytes);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithEmptyPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        char[] charBuf = new char[0];
        MessageType type = null;
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                while (count != -1) {
                    count = reader.read(charBuf);
                    if (count != -1) {
                        charsRead += count;
                    }
                }
                assertEquals(0, charsRead);
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        messageWriter.writeFully(charBuf);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.0.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithEmptyPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();

        char[] charBuf = new char[0];
        MessageType type = null;
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                while (count != -1) {
                    count = reader.read(charBuf);
                    if (count != -1) {
                        charsRead += count;
                    }
                }
                assertEquals(0, charsRead);
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        messageWriter.writeFully(charBuf);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        char[] charBuf = new char[125];
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                int offset = 0;
                while ((count != -1) && (offset < charBuf.length)) {
                    count = reader.read(charBuf, offset, charBuf.length - offset);
                    if (count != -1) {
                        charsRead += count;
                        offset += count;
                    }
                }
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        char[] text = new char[charsRead];
        System.arraycopy(charBuf, 0, text, 0, charsRead);
        messageWriter.writeFully(text);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.125.fragmented.but.not.utf8.aligned/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedEvenWhenNotUTF8Aligned() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        char[] charBuf = new char[125];
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                int offset = 0;
                while ((count != -1) && (offset < charBuf.length)) {
                    count = reader.read(charBuf, offset, charBuf.length - offset);
                    if (count != -1) {
                        charsRead += count;
                        offset += count;
                    }
                }
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        char[] text = new char[charsRead];
        System.arraycopy(charBuf, 0, text, 0, charsRead);
        messageWriter.writeFully(text);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.125.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        char[] charBuf = new char[125];
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                int offset = 0;
                while ((count != -1) && (offset < charBuf.length)) {
                    count = reader.read(charBuf, offset, charBuf.length - offset);
                    if (count != -1) {
                        charsRead += count;
                        offset += count;
                    }
                }
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        char[] text = new char[charsRead];
        System.arraycopy(charBuf, 0, text, 0, charsRead);
        messageWriter.writeFully(text);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.125.fragmented.with.some.empty.fragments/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedWithSomeEmptyFragments() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        char[] charBuf = new char[125];
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                int offset = 0;
                while ((count != -1) && (offset < charBuf.length)) {
                    count = reader.read(charBuf, offset, charBuf.length - offset);
                    if (count != -1) {
                        charsRead += count;
                        offset += count;
                    }
                }
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        char[] text = new char[charsRead];
        System.arraycopy(charBuf, 0, text, 0, charsRead);
        messageWriter.writeFully(text);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoServerSendTextFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageWriter messageWriter = connection.getMessageWriter();
        MessageType type = null;
        char[] charBuf = new char[125];
        int charsRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert !messageReader.streaming();

            switch (type) {
            case TEXT:
                charsRead = messageReader.readFully(charBuf);
                break;
            default:
                assertSame(TEXT, type);
                break;
            }
        }

        char[] text = new char[charsRead];
        System.arraycopy(charBuf, 0, text, 0, charsRead);
        messageWriter.writeFully(text);
        k3po.finish();
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinued() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[50];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        try {
            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinuedUsingReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinuedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];

        try {
            input.read(readBytes);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
    "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];

        try {
            input.read(readBytes);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmentedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];

        try {
            input.read(readBytes);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmentedUsingReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmentedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];

        try {
            input.read(readBytes);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];

        try {
            input.read(readBytes);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];

        try {
            reader.read(readChars);
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();
        MessageType type = null;
        byte[] readBytes = new byte[125];
        int bytesRead = 0;
        int count = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                assert messageReader.streaming();

                switch (type) {
                case BINARY:
                    InputStream in = messageReader.getInputStream();
                    int offset = 0;
                    while ((count != -1) && (offset < readBytes.length)) {
                        count = in.read(readBytes, offset, readBytes.length - offset);
                        if (count != -1) {
                            bytesRead += count;
                            offset += count;
                        }
                    }
                    assertEquals(125, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    private static class RandomString {

        private static final char[] SYMBOLS;

        static {
            StringBuilder symbols = new StringBuilder();
            for (char ch = 32; ch <= 126; ++ch) {
                symbols.append(ch);
            }
            SYMBOLS = symbols.toString().toCharArray();
        }

        private final Random random = new Random();

        private final char[] buf;

        public RandomString(int length) {
          if (length < 1) {
            throw new IllegalArgumentException("length < 1: " + length);
          }
          buf = new char[length];
        }

        public String nextString() {
          for (int idx = 0; idx < buf.length; ++idx) {
            buf[idx] = SYMBOLS[random.nextInt(SYMBOLS.length)];
          }

          return new String(buf);
        }
    }
}
