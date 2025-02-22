/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.appender;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.encoder.DummyEncoder;
import ch.qos.logback.core.encoder.EchoEncoder;
import ch.qos.logback.core.encoder.NopEncoder;
import ch.qos.logback.core.layout.DummyLayout;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.testUtil.StatusChecker;

import org.fusesource.jansi.AnsiPrintStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Redirecting System.out is quite messy. Disable this test in Maven but not in Package.class
 */
public class ConsoleAppenderTest extends AbstractAppenderTest<Object> {

    XTeeOutputStream teeOut;
    XTeeOutputStream teeErr;
    PrintStream originalOut;
    PrintStream originalErr;

    @Before
    public void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        // teeOut will output bytes on System out but it will also
        // collect them so that the output can be compared against
        // some expected output data
        // teeOut = new TeeOutputStream(originalOut);

        // keep the console quiet
        teeOut = new XTeeOutputStream(null);
        teeErr = new XTeeOutputStream(null);

        // redirect System.out to teeOut and System.err to teeErr
        System.setOut(new PrintStream(teeOut));
        System.setErr(new PrintStream(teeErr));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Override
    public Appender<Object> getAppender() {
        return new ConsoleAppender<Object>();
    }

    protected Appender<Object> getConfiguredAppender() {
        ConsoleAppender<Object> ca = new ConsoleAppender<Object>();
        ca.setEncoder(new NopEncoder<Object>());
        ca.start();
        return ca;
    }

    @Test
    public void smoke() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        ca.setEncoder(new DummyEncoder<Object>());
        ca.start();
        ca.doAppend(new Object());
        assertEquals(DummyLayout.DUMMY, teeOut.toString());
    }

    @Test
    public void open() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        DummyEncoder<Object> dummyEncoder = new DummyEncoder<Object>();
        dummyEncoder.setFileHeader("open");
        ca.setEncoder(dummyEncoder);
        ca.start();
        ca.doAppend(new Object());
        ca.stop();
        assertEquals("open" + CoreConstants.LINE_SEPARATOR + DummyLayout.DUMMY, teeOut.toString());
    }

    @Test
    public void testClose() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        DummyEncoder<Object> dummyEncoder = new DummyEncoder<Object>();
        dummyEncoder.setFileFooter("CLOSED");
        ca.setEncoder(dummyEncoder);
        ca.start();
        ca.doAppend(new Object());
        ca.stop();
        // ConsoleAppender must keep the underlying stream open.
        // The console is not ours to close.
        assertFalse(teeOut.isClosed());
        assertEquals(DummyLayout.DUMMY + "CLOSED", teeOut.toString());
    }

    // See http://jira.qos.ch/browse/LBCORE-143
    @Test
    public void changeInConsole() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        EchoEncoder<Object> encoder = new EchoEncoder<Object>();
        ca.setEncoder(encoder);
        ca.start();
        ca.doAppend("a");
        assertEquals("a" + CoreConstants.LINE_SEPARATOR, teeOut.toString());

        XTeeOutputStream newTee = new XTeeOutputStream(null);
        System.setOut(new PrintStream(newTee));
        ca.doAppend("b");
        assertEquals("b" + CoreConstants.LINE_SEPARATOR, newTee.toString());
    }

    @Test
    public void testUTF16BE() throws UnsupportedEncodingException {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        DummyEncoder<Object> dummyEncoder = new DummyEncoder<Object>();
        Charset utf16BE = Charset.forName("UTF-16BE");
        dummyEncoder.setCharset(utf16BE);
        ca.setEncoder(dummyEncoder);
        ca.start();
        ca.doAppend(new Object());
        assertEquals(DummyLayout.DUMMY, new String(teeOut.toByteArray(), utf16BE));
    }

    @Test
    public void wrongTarget() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        EchoEncoder<Object> encoder = new EchoEncoder<Object>();
        encoder.setContext(context);
        ca.setContext(context);
        ca.setTarget("foo");
        ca.setEncoder(encoder);
        ca.start();
        ca.doAppend("a");
        StatusChecker checker = new StatusChecker(context);
        // 21:28:01,246 + WARN in ch.qos.logback.core.ConsoleAppender[null] - [foo] should be one of [System.out,
        // System.err]
        // 21:28:01,246 |-WARN in ch.qos.logback.core.ConsoleAppender[null] - Using previously set target, System.out by
        // default.
        // StatusPrinter.print(context);

        checker.assertContainsMatch(Status.WARN, "\\[foo\\] should be one of \\[System.out, System.err\\]");

    }

    @Test
    public void jansiSystemOut() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        DummyEncoder<Object> dummyEncoder = new DummyEncoder<Object>();
        ca.setEncoder(dummyEncoder);
        ca.setTarget("System.out");
        ca.setContext(context);
        ca.setWithJansi(true);
        ca.start();
        assertTrue(ca.getOutputStream() instanceof AnsiPrintStream);

        /*
        After Jansi 2.0 version, the current terminal stream cannot be wrapped (currently I have not found a way).
        Therefore, the test that compares the printed content will fail.
         */

        // ca.doAppend(new Object());
        // assertEquals(DummyLayout.DUMMY, teeOut.toString());
    }

    @Test
    public void jansiSystemErr() {
        ConsoleAppender<Object> ca = (ConsoleAppender<Object>) getAppender();
        DummyEncoder<Object> dummyEncoder = new DummyEncoder<Object>();
        ca.setEncoder(dummyEncoder);
        ca.setTarget("System.err");
        ca.setContext(context);
        ca.setWithJansi(true);
        ca.start();
        assertTrue(ca.getOutputStream() instanceof AnsiPrintStream);

        /*
        After Jansi 2.0 version, the current terminal stream cannot be wrapped (currently I have not found a way).
        Therefore, the test that compares the printed content will fail.
         */

        // ca.doAppend(new Object());
        // assertEquals(DummyLayout.DUMMY, teeErr.toString());
    }
}
