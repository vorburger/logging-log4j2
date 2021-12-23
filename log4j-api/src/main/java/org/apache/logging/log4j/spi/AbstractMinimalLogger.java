package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.internal.DefaultLogBuilder;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.LambdaUtil;
import org.apache.logging.log4j.util.MessageSupplier;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StackLocatorUtil;
import org.apache.logging.log4j.util.Supplier;

/**
 * Base implementation of a minimal Logger.
 * This class intentionally does not, and never should, implement Serializable because not all environments require
 * it - and correctly supporting Serializable adds extra requirements which have security implications.
 * This class intentionally does not support MessageFactory either, and leaves that to subclasses.
 * This class intentionally does not implement traceEntry() and traceExit(); sublasses will either have to implement it, or could use the EntryExitNoOpLogger.
 */
public abstract class AbstractMinimalLogger implements ExtendedLogger, LocationAwareLogger {

    private static final ThreadLocal<int[]> recursionDepthHolder = new ThreadLocal<>(); // LOG4J2-1518, LOG4J2-2031
    private static final String THROWING = "Throwing";
    private static final String CATCHING = "Catching";

    // TODO Should this be turned into a protected method instead, so that subclasses can override it? 
    protected static final String FQCN = AbstractMinimalLogger.class.getName();

    /**
     * Marker for exception tracing.
     */
    public static final Marker EXCEPTION_MARKER = MarkerManager.getMarker("EXCEPTION");

    /**
     * Marker for throwing exceptions.
     */
    public static final Marker THROWING_MARKER = MarkerManager.getMarker("THROWING").setParents(EXCEPTION_MARKER);

    /**
     * Marker for catching exceptions.
     */
    public static final Marker CATCHING_MARKER = MarkerManager.getMarker("CATCHING").setParents(EXCEPTION_MARKER);

    protected final String name;
    protected final MessageFactory2 messageFactory;
    protected final transient ThreadLocal<DefaultLogBuilder> logBuilder;

    @Deprecated
    protected AbstractMinimalLogger(MessageFactory2 messageFactory) {
        this.name = getClass().getName();
        this.messageFactory = messageFactory;
        this.logBuilder = new LocalLogBuilder(this);
    }

    protected AbstractMinimalLogger(String name, MessageFactory2 messageFactory) {
        this.name = name;
        this.messageFactory = messageFactory;
        this.logBuilder = new LocalLogBuilder(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override public <MF extends MessageFactory> MF getMessageFactory() {
        return (MF) messageFactory;
    }

    @Override
    public void logMessage(final Level level, final Marker marker, final String fqcn, final StackTraceElement location,
        final Message message, final Throwable throwable) {
        try {
            incrementRecursionDepth();
            log(level, marker, fqcn, location, message, throwable);
        } catch (Throwable ex) {
            handleLogMessageException(ex, fqcn, message);
        } finally {
            decrementRecursionDepth();
            ReusableMessageFactory.release(message);
        }
    }

    @Override
    public <T extends Throwable> T throwing(final T throwable) {
        return throwing(FQCN, Level.ERROR, throwable);
    }

    @Override
    public <T extends Throwable> T throwing(final Level level, final T throwable) {
        return throwing(FQCN, level, throwable);
    }

    /**
     * Logs a Throwable to be thrown.
     *
     * @param <T> the type of the Throwable.
     * @param fqcn the fully qualified class name of this Logger implementation.
     * @param level The logging Level.
     * @param throwable The Throwable.
     * @return the Throwable.
     */
    protected <T extends Throwable> T throwing(final String fqcn, final Level level, final T throwable) {
        if (isEnabled(level, THROWING_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, level, THROWING_MARKER, throwingMsg(throwable), throwable);
        }
        return throwable;
    }

    protected Message throwingMsg(final Throwable throwable) {
        return messageFactory.newMessage(THROWING);
    }

    @Override
    public void catching(final Level level, final Throwable throwable) {
        catching(FQCN, level, throwable);
    }

    /**
     * Logs a Throwable that has been caught with location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param level The logging level.
     * @param throwable The Throwable.
     */
    protected void catching(final String fqcn, final Level level, final Throwable throwable) {
        if (isEnabled(level, CATCHING_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, level, CATCHING_MARKER, catchingMsg(throwable), throwable);
        }
    }

    @Override
    public void catching(final Throwable throwable) {
        if (isEnabled(Level.ERROR, CATCHING_MARKER, (Object) null, null)) {
            logMessageSafely(FQCN, Level.ERROR, CATCHING_MARKER, catchingMsg(throwable), throwable);
        }
    }

    protected Message catchingMsg(final Throwable throwable) {
        return messageFactory.newMessage(CATCHING);
    }

    @Override
    public void trace(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void trace(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
    }

    @Override
    public void trace(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, null);
    }

    @Override
    public void trace(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
    }

    @Override
    public void trace(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, null);
    }

    @Override
    public void trace(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
    }

    @Override
    public void trace(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, (Throwable) null);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, params);
    }

    @Override
    public void trace(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
    }

    @Override
    public void trace(final Message message) {
        logIfEnabled(FQCN, Level.TRACE, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void trace(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
    }

    @Override
    public void trace(final CharSequence message) {
        logIfEnabled(FQCN, Level.TRACE, null, message, null);
    }

    @Override
    public void trace(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
    }

    @Override
    public void trace(final Object message) {
        logIfEnabled(FQCN, Level.TRACE, null, message, null);
    }

    @Override
    public void trace(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
    }

    @Override
    public void trace(final String message) {
        logIfEnabled(FQCN, Level.TRACE, null, message, (Throwable) null);
    }

    @Override
    public void trace(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.TRACE, null, message, params);
    }

    @Override
    public void trace(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
    }

    @Override
    public void trace(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void trace(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, throwable);
    }

    @Override
    public void trace(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void trace(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, paramSuppliers);
    }

    @Override
    public void trace(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, throwable);
    }

    @Override
    public void trace(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.TRACE, null, message, paramSuppliers);
    }

    @Override
    public void trace(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void trace(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, throwable);
    }

    @Override
    public void trace(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void trace(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, throwable);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void trace(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void debug(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, null);
    }

    @Override
    public void debug(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
    }

    @Override
    public void debug(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void debug(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
    }

    @Override
    public void debug(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, null);
    }

    @Override
    public void debug(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
    }

    @Override
    public void debug(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, (Throwable) null);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, params);
    }

    @Override
    public void debug(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
    }

    @Override
    public void debug(final Message message) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void debug(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
    }

    @Override
    public void debug(final CharSequence message) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, null);
    }

    @Override
    public void debug(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
    }

    @Override
    public void debug(final Object message) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, null);
    }

    @Override
    public void debug(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
    }

    @Override
    public void debug(final String message) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, (Throwable) null);
    }

    @Override
    public void debug(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, params);
    }

    @Override
    public void debug(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
    }

    @Override
    public void debug(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void debug(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, throwable);
    }

    @Override
    public void debug(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void debug(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, paramSuppliers);
    }

    @Override
    public void debug(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, throwable);
    }

    @Override
    public void debug(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, paramSuppliers);
    }

    @Override
    public void debug(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void debug(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, throwable);
    }

    @Override
    public void debug(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void debug(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, throwable);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void debug(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void info(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.INFO, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void info(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
    }

    @Override
    public void info(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.INFO, marker, message, null);
    }

    @Override
    public void info(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
    }

    @Override
    public void info(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.INFO, marker, message, null);
    }

    @Override
    public void info(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
    }

    @Override
    public void info(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.INFO, marker, message, (Throwable) null);
    }

    @Override
    public void info(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.INFO, marker, message, params);
    }

    @Override
    public void info(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
    }

    @Override
    public void info(final Message message) {
        logIfEnabled(FQCN, Level.INFO, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void info(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, message, throwable);
    }

    @Override
    public void info(final CharSequence message) {
        logIfEnabled(FQCN, Level.INFO, null, message, null);
    }

    @Override
    public void info(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, message, throwable);
    }

    @Override
    public void info(final Object message) {
        logIfEnabled(FQCN, Level.INFO, null, message, null);
    }

    @Override
    public void info(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, message, throwable);
    }

    @Override
    public void info(final String message) {
        logIfEnabled(FQCN, Level.INFO, null, message, (Throwable) null);
    }

    @Override
    public void info(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.INFO, null, message, params);
    }

    @Override
    public void info(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, message, throwable);
    }

    @Override
    public void info(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.INFO, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void info(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, messageSupplier, throwable);
    }

    @Override
    public void info(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void info(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.INFO, marker, message, paramSuppliers);
    }

    @Override
    public void info(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, throwable);
    }

    @Override
    public void info(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.INFO, null, message, paramSuppliers);
    }

    @Override
    public void info(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void info(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, throwable);
    }

    @Override
    public void info(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.INFO, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void info(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.INFO, null, messageSupplier, throwable);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void info(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void warn(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.WARN, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void warn(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
    }

    @Override
    public void warn(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.WARN, marker, message, null);
    }

    @Override
    public void warn(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
    }

    @Override
    public void warn(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.WARN, marker, message, null);
    }

    @Override
    public void warn(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
    }

    @Override
    public void warn(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.WARN, marker, message, (Throwable) null);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.WARN, marker, message, params);
    }

    @Override
    public void warn(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
    }

    @Override
    public void warn(final Message message) {
        logIfEnabled(FQCN, Level.WARN, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void warn(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, message, throwable);
    }

    @Override
    public void warn(final CharSequence message) {
        logIfEnabled(FQCN, Level.WARN, null, message, null);
    }

    @Override
    public void warn(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, message, throwable);
    }

    @Override
    public void warn(final Object message) {
        logIfEnabled(FQCN, Level.WARN, null, message, null);
    }

    @Override
    public void warn(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, message, throwable);
    }

    @Override
    public void warn(final String message) {
        logIfEnabled(FQCN, Level.WARN, null, message, (Throwable) null);
    }

    @Override
    public void warn(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.WARN, null, message, params);
    }

    @Override
    public void warn(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, message, throwable);
    }

    @Override
    public void warn(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.WARN, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void warn(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, messageSupplier, throwable);
    }

    @Override
    public void warn(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void warn(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.WARN, marker, message, paramSuppliers);
    }

    @Override
    public void warn(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, throwable);
    }

    @Override
    public void warn(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.WARN, null, message, paramSuppliers);
    }

    @Override
    public void warn(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void warn(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, throwable);
    }

    @Override
    public void warn(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.WARN, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void warn(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.WARN, null, messageSupplier, throwable);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void warn(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void error(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void error(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
    }

    @Override
    public void error(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, null);
    }

    @Override
    public void error(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
    }

    @Override
    public void error(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, null);
    }

    @Override
    public void error(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
    }

    @Override
    public void error(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, (Throwable) null);
    }

    @Override
    public void error(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, params);
    }

    @Override
    public void error(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
    }

    @Override
    public void error(final Message message) {
        logIfEnabled(FQCN, Level.ERROR, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void error(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
    }

    @Override
    public void error(final CharSequence message) {
        logIfEnabled(FQCN, Level.ERROR, null, message, null);
    }

    @Override
    public void error(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
    }

    @Override
    public void error(final Object message) {
        logIfEnabled(FQCN, Level.ERROR, null, message, null);
    }

    @Override
    public void error(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
    }

    @Override
    public void error(final String message) {
        logIfEnabled(FQCN, Level.ERROR, null, message, (Throwable) null);
    }

    @Override
    public void error(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.ERROR, null, message, params);
    }

    @Override
    public void error(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
    }

    @Override
    public void error(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void error(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, throwable);
    }

    @Override
    public void error(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void error(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, paramSuppliers);
    }

    @Override
    public void error(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, throwable);
    }

    @Override
    public void error(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.ERROR, null, message, paramSuppliers);
    }

    @Override
    public void error(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void error(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, throwable);
    }

    @Override
    public void error(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void error(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, throwable);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void error(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void fatal(final Marker marker, final Message message) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void fatal(final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
    }

    @Override
    public void fatal(final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, null);
    }

    @Override
    public void fatal(final Marker marker, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
    }

    @Override
    public void fatal(final Marker marker, final Object message) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, null);
    }

    @Override
    public void fatal(final Marker marker, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
    }

    @Override
    public void fatal(final Marker marker, final String message) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, (Throwable) null);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, params);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
    }

    @Override
    public void fatal(final Message message) {
        logIfEnabled(FQCN, Level.FATAL, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void fatal(final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
    }

    @Override
    public void fatal(final CharSequence message) {
        logIfEnabled(FQCN, Level.FATAL, null, message, null);
    }

    @Override
    public void fatal(final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
    }

    @Override
    public void fatal(final Object message) {
        logIfEnabled(FQCN, Level.FATAL, null, message, null);
    }

    @Override
    public void fatal(final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
    }

    @Override
    public void fatal(final String message) {
        logIfEnabled(FQCN, Level.FATAL, null, message, (Throwable) null);
    }

    @Override
    public void fatal(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.FATAL, null, message, params);
    }

    @Override
    public void fatal(final String message, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
    }

    @Override
    public void fatal(final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void fatal(final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, throwable);
    }

    @Override
    public void fatal(final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, paramSuppliers);
    }

    @Override
    public void fatal(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, throwable);
    }

    @Override
    public void fatal(final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, Level.FATAL, null, message, paramSuppliers);
    }

    @Override
    public void fatal(final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void fatal(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, throwable);
    }

    @Override
    public void fatal(final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void fatal(final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, throwable);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2,
            final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void fatal(final String message, final Object p0) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG, null, null);
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return isEnabled(Level.DEBUG, marker, (Object) null, null);
    }

    @Override
    public boolean isEnabled(final Level level) {
        return isEnabled(level, null, (Object) null, null);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker) {
        return isEnabled(level, marker, (Object) null, null);
    }

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR, null, (Object) null, null);
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return isEnabled(Level.ERROR, marker, (Object) null, null);
    }

    @Override
    public boolean isFatalEnabled() {
        return isEnabled(Level.FATAL, null, (Object) null, null);
    }

    @Override
    public boolean isFatalEnabled(final Marker marker) {
        return isEnabled(Level.FATAL, marker, (Object) null, null);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO, null, (Object) null, null);
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return isEnabled(Level.INFO, marker, (Object) null, null);
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(Level.TRACE, null, (Object) null, null);
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return isEnabled(Level.TRACE, marker, (Object) null, null);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN, null, (Object) null, null);
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return isEnabled(Level.WARN, marker, (Object) null, null);
    }

    @Override
    public void log(final Level level, final Marker marker, final Message message) {
        logIfEnabled(FQCN, level, marker, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void log(final Level level, final Marker marker, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, level, marker, message, throwable);
    }

    @Override
    public void log(final Level level, final Marker marker, final CharSequence message) {
        logIfEnabled(FQCN, level, marker, message, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Marker marker, final CharSequence message, final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessage(FQCN, level, marker, message, throwable);
        }
    }

    @Override
    public void log(final Level level, final Marker marker, final Object message) {
        logIfEnabled(FQCN, level, marker, message, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Marker marker, final Object message, final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessage(FQCN, level, marker, message, throwable);
        }
    }

    @Override
    public void log(final Level level, final Marker marker, final String message) {
        logIfEnabled(FQCN, level, marker, message, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object... params) {
        logIfEnabled(FQCN, level, marker, message, params);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, level, marker, message, throwable);
    }

    @Override
    public void log(final Level level, final Message message) {
        logIfEnabled(FQCN, level, null, message, message != null ? message.getThrowable() : null);
    }

    @Override
    public void log(final Level level, final Message message, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, message, throwable);
    }

    @Override
    public void log(final Level level, final CharSequence message) {
        logIfEnabled(FQCN, level, null, message, null);
    }

    @Override
    public void log(final Level level, final CharSequence message, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, message, throwable);
    }

    @Override
    public void log(final Level level, final Object message) {
        logIfEnabled(FQCN, level, null, message, null);
    }

    @Override
    public void log(final Level level, final Object message, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, message, throwable);
    }

    @Override
    public void log(final Level level, final String message) {
        logIfEnabled(FQCN, level, null, message, (Throwable) null);
    }

    @Override
    public void log(final Level level, final String message, final Object... params) {
        logIfEnabled(FQCN, level, null, message, params);
    }

    @Override
    public void log(final Level level, final String message, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, message, throwable);
    }

    @Override
    public void log(final Level level, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, level, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, messageSupplier, throwable);
    }

    @Override
    public void log(final Level level, final Marker marker, final Supplier<?> messageSupplier) {
        logIfEnabled(FQCN, level, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, level, marker, message, paramSuppliers);
    }

    @Override
    public void log(final Level level, final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, level, marker, messageSupplier, throwable);
    }

    @Override
    public void log(final Level level, final String message, final Supplier<?>... paramSuppliers) {
        logIfEnabled(FQCN, level, null, message, paramSuppliers);
    }

    @Override
    public void log(final Level level, final Marker marker, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, level, marker, messageSupplier, (Throwable) null);
    }

    @Override
    public void log(final Level level, final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, level, marker, messageSupplier, throwable);
    }

    @Override
    public void log(final Level level, final MessageSupplier messageSupplier) {
        logIfEnabled(FQCN, level, null, messageSupplier, (Throwable) null);
    }

    @Override
    public void log(final Level level, final MessageSupplier messageSupplier, final Throwable throwable) {
        logIfEnabled(FQCN, level, null, messageSupplier, throwable);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0) {
        logIfEnabled(FQCN, level, marker, message, p0);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, level, marker, message, p0, p1);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4, final Object p5) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1,
            final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void log(final Level level, final String message, final Object p0) {
        logIfEnabled(FQCN, level, null, message, p0);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1) {
        logIfEnabled(FQCN, level, null, message, p0, p1);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6, final Object p7, final Object p8, final Object p9) {
        logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Message message,
            final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessageSafely(fqcn, level, marker, message, throwable);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker,
            final MessageSupplier messageSupplier, final Throwable throwable) {
        if (isEnabled(level, marker, messageSupplier, throwable)) {
            logMessage(fqcn, level, marker, messageSupplier, throwable);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Object message,
            final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessage(fqcn, level, marker, message, throwable);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final CharSequence message,
            final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessage(fqcn, level, marker, message, throwable);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Supplier<?> messageSupplier,
            final Throwable throwable) {
        if (isEnabled(level, marker, messageSupplier, throwable)) {
            logMessage(fqcn, level, marker, messageSupplier, throwable);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message) {
        if (isEnabled(level, marker, message)) {
            logMessage(fqcn, level, marker, message);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Supplier<?>... paramSuppliers) {
        if (isEnabled(level, marker, message)) {
            logMessage(fqcn, level, marker, message, paramSuppliers);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object... params) {
        if (isEnabled(level, marker, message, params)) {
            logMessage(fqcn, level, marker, message, params);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0) {
        if (isEnabled(level, marker, message, p0)) {
            logMessage(fqcn, level, marker, message, p0);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1) {
        if (isEnabled(level, marker, message, p0, p1)) {
            logMessage(fqcn, level, marker, message, p0, p1);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2) {
        if (isEnabled(level, marker, message, p0, p1, p2)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        if (isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9)) {
            logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        }
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
            final Throwable throwable) {
        if (isEnabled(level, marker, message, throwable)) {
            logMessage(fqcn, level, marker, message, throwable);
        }
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final CharSequence message,
            final Throwable throwable) {
        logMessageSafely(fqcn, level, marker, messageFactory.newMessage(message), throwable);
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final Object message,
            final Throwable throwable) {
        logMessageSafely(fqcn, level, marker, messageFactory.newMessage(message), throwable);
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker,
            final MessageSupplier messageSupplier, final Throwable throwable) {
        final Message message = LambdaUtil.get(messageSupplier);
        final Throwable effectiveThrowable = (throwable == null && message != null)
                ? message.getThrowable()
                : throwable;
        logMessageSafely(fqcn, level, marker, message, effectiveThrowable);
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final Supplier<?> messageSupplier,
            final Throwable throwable) {
        final Message message = LambdaUtil.getMessage(messageSupplier, messageFactory);
        final Throwable effectiveThrowable = (throwable == null && message != null)
                ? message.getThrowable()
                : throwable;
        logMessageSafely(fqcn, level, marker, message, effectiveThrowable);
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Throwable throwable) {
        logMessageSafely(fqcn, level, marker, messageFactory.newMessage(message), throwable);
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message) {
        final Message msg = messageFactory.newMessage(message);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object... params) {
        final Message msg = messageFactory.newMessage(message, params);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0) {
        final Message msg = messageFactory.newMessage(message, p0);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1) {
        final Message msg = messageFactory.newMessage(message, p0, p1);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5,
            final Object p6, final Object p7, final Object p8, final Object p9) {
        final Message msg = messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message,
            final Supplier<?>... paramSuppliers) {
        final Message msg = messageFactory.newMessage(message, LambdaUtil.getAll(paramSuppliers));
        logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
    }

    protected void log(final Level level, final Marker marker, final String fqcn, final StackTraceElement location,
        final Message message, final Throwable throwable) {
        logMessage(fqcn, level, marker, message, throwable);
    }

    @Override
    public void printf(final Level level, final Marker marker, final String format, final Object... params) {
        if (isEnabled(level, marker, format, params)) {
            final Message message = new StringFormattedMessage(format, params);
            logMessageSafely(FQCN, level, marker, message, message.getThrowable());
        }
    }

    @Override
    public void printf(final Level level, final String format, final Object... params) {
        if (isEnabled(level, null, format, params)) {
            final Message message = new StringFormattedMessage(format, params);
            logMessageSafely(FQCN, level, null, message, message.getThrowable());
        }
    }

    /**
     * Construct a trace log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atTrace() {
        return atLevel(Level.TRACE);
    }

    /**
     * Construct a debug log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atDebug() {
        return atLevel(Level.DEBUG);
    }

    /**
     * Construct an informational log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atInfo() {
        return atLevel(Level.INFO);
    }

    /**
     * Construct a warning log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atWarn() {
        return atLevel(Level.WARN);
    }

    /**
     * Construct an error log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atError() {
        return atLevel(Level.ERROR);
    }

    /**
     * Construct a fatal log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atFatal() {
        return atLevel(Level.FATAL);
    }

    /**
     * Construct a fatal log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder always() {
        DefaultLogBuilder builder = logBuilder.get();
        if (builder.isInUse()) {
            return new DefaultLogBuilder(this);
        }
        return builder.reset(Level.OFF);
    }

    /**
     * Construct a log event.
     * @return a LogBuilder.
     * @since 2.13.0
     */
    @Override
    public LogBuilder atLevel(Level level) {
        if (isEnabled(level)) {
            return getLogBuilder(level).reset(level);
        } else {
            return LogBuilder.NOOP;
        }
    }

    private DefaultLogBuilder getLogBuilder(Level level) {
        DefaultLogBuilder builder = logBuilder.get();
        return Constants.ENABLE_THREADLOCALS && !builder.isInUse() ? builder : new DefaultLogBuilder(this, level);
    }

    protected class LocalLogBuilder extends ThreadLocal<DefaultLogBuilder> {
        private AbstractMinimalLogger logger;
        LocalLogBuilder(AbstractMinimalLogger logger) {
            this.logger = logger;
        }

        @Override
        protected DefaultLogBuilder initialValue() {
            return new DefaultLogBuilder(logger);
        }
    }

    protected boolean requiresLocation() {
        return false;
    }

    @PerformanceSensitive
    // NOTE: This is a hot method. Current implementation compiles to 15 bytes of byte code.
    // This is within the 35 byte MaxInlineSize threshold. Modify with care!
    protected StackTraceElement getLocation(String fqcn) {
        return requiresLocation() ? StackLocatorUtil.calcLocation(fqcn) : null;
    }

    @PerformanceSensitive
    // NOTE: This is a hot method. Current implementation compiles to 30 bytes of byte code.
    // This is within the 35 byte MaxInlineSize threshold. Modify with care!
    protected void logMessageSafely(final String fqcn, final Level level, final Marker marker, final Message message,
            final Throwable throwable) {
        try {
            logMessageTrackRecursion(fqcn, level, marker, message, throwable);
        } finally {
            // LOG4J2-1583 prevent scrambled logs when logging calls are nested (logging in toString())
            ReusableMessageFactory.release(message);
        }
    }

    @PerformanceSensitive
    // NOTE: This is a hot method. Current implementation compiles to 33 bytes of byte code.
    // This is within the 35 byte MaxInlineSize threshold. Modify with care!
    private void logMessageTrackRecursion(final String fqcn,
                                          final Level level,
                                          final Marker marker,
                                          final Message message,
                                          final Throwable throwable) {
        try {
            incrementRecursionDepth(); // LOG4J2-1518, LOG4J2-2031
            tryLogMessage(fqcn, getLocation(fqcn), level, marker, message, throwable);
        } finally {
            decrementRecursionDepth();
        }
    }

    @PerformanceSensitive
    // NOTE: This is a hot method. Current implementation compiles to 26 bytes of byte code.
    // This is within the 35 byte MaxInlineSize threshold. Modify with care!
    private void tryLogMessage(final String fqcn,
                               final StackTraceElement location,
                               final Level level,
                               final Marker marker,
                               final Message message,
                               final Throwable throwable) {
        try {
            log(level, marker, fqcn, location, message, throwable);
        } catch (final Throwable t) {
            // LOG4J2-1990 Log4j2 suppresses all exceptions that occur once application called the logger
            handleLogMessageException(t, fqcn, message);
        }
    }

    // LOG4J2-1990 Log4j2 suppresses all exceptions that occur once application called the logger
    // TODO Configuration setting to propagate exceptions back to the caller *if requested*
    private void handleLogMessageException(final Throwable throwable, final String fqcn, final Message message) {
        if (throwable instanceof LoggingException) {
            throw (LoggingException) throwable;
        }
        StatusLogger.getLogger().warn("{} caught {} logging {}: {}",
                fqcn,
                throwable.getClass().getName(),
                message.getClass().getSimpleName(),
                message.getFormat(),
                throwable);
    }

    private static int[] getRecursionDepthHolder() {
        int[] result = recursionDepthHolder.get();
        if (result == null) {
            result = new int[1];
            recursionDepthHolder.set(result);
        }
        return result;
    }

    private static void incrementRecursionDepth() {
        getRecursionDepthHolder()[0]++;
    }

    private static void decrementRecursionDepth() {
        int newDepth = --getRecursionDepthHolder()[0];
        if (newDepth < 0) {
            throw new IllegalStateException("Recursion depth became negative: " + newDepth);
        }
    }

    /**
     * Returns the depth of nested logging calls in the current Thread: zero if no logging call has been made,
     * one if a single logging call without nested logging calls has been made, or more depending on the level of
     * nesting.
     * @return the depth of the nested logging calls in the current Thread
     */
    public static int getRecursionDepth() {
        return getRecursionDepthHolder()[0];
    }
}
