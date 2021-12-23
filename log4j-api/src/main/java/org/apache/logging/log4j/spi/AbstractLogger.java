/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.spi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.message.DefaultFlowMessageFactory;
import org.apache.logging.log4j.message.EntryMessage;
import org.apache.logging.log4j.message.FlowMessageFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.MessageSupplier;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;
import org.apache.logging.log4j.util.Supplier;

/**
 * Base implementation of a Logger. It is highly recommended that any Logger implementation extend this class.
 */
public abstract class AbstractLogger extends AbstractMinimalLogger implements ExtendedLogger, Serializable {
    // Implementation note: many methods in this class are tuned for performance. MODIFY WITH CARE!
    // Specifically, try to keep the hot methods to 35 bytecodes or less:
    // this is within the MaxInlineSize threshold on Java 7 and Java 8 Hotspot and makes these methods
    // candidates for immediate inlining instead of waiting until they are designated "hot enough".

    /**
     * Marker for flow tracing.
     */
    public static final Marker FLOW_MARKER = MarkerManager.getMarker("FLOW");

    /**
     * Marker for method entry tracing.
     */
    public static final Marker ENTRY_MARKER = MarkerManager.getMarker("ENTER").setParents(FLOW_MARKER);

    /**
     * Marker for method exit tracing.
     */
    public static final Marker EXIT_MARKER = MarkerManager.getMarker("EXIT").setParents(FLOW_MARKER);

    /**
     * Marker for exception tracing.
     */
    public static final Marker EXCEPTION_MARKER = AbstractMinimalLogger.EXCEPTION_MARKER;

    /**
     * Marker for throwing exceptions.
     */
    public static final Marker THROWING_MARKER = AbstractMinimalLogger.THROWING_MARKER;

    /**
     * Marker for catching exceptions.
     */
    public static final Marker CATCHING_MARKER = AbstractMinimalLogger.CATCHING_MARKER;

    /**
     * The default MessageFactory class.
     */
    public static final Class<? extends MessageFactory> DEFAULT_MESSAGE_FACTORY_CLASS =
            createClassForProperty("log4j2.messageFactory", ReusableMessageFactory.class,
                    ParameterizedMessageFactory.class);

    /**
     * The default FlowMessageFactory class.
     */
    public static final Class<? extends FlowMessageFactory> DEFAULT_FLOW_MESSAGE_FACTORY_CLASS =
            createFlowClassForProperty("log4j2.flowMessageFactory", DefaultFlowMessageFactory.class);

    private static final long serialVersionUID = 2L;

    private final FlowMessageFactory flowMessageFactory;

    /**
     * Creates a new logger named after this class (or subclass).
     */
    @Deprecated
    public AbstractLogger() {
        super(createDefaultMessageFactory());
        this.flowMessageFactory = createDefaultFlowMessageFactory();
    }

    /**
     * Creates a new named logger.
     *
     * @param name the logger name
     */
    public AbstractLogger(final String name) {
        this(name, createDefaultMessageFactory());
    }

    /**
     * Creates a new named logger with a particular {@link MessageFactory}.
     *
     * @param name the logger name
     * @param messageFactory the message factory, if null then use the default message factory.
     */
    public AbstractLogger(final String name, final MessageFactory messageFactory) {
        super(name, messageFactory == null ? createDefaultMessageFactory() : narrow(messageFactory));
        this.flowMessageFactory = createDefaultFlowMessageFactory();
    }

    /**
     * Checks that the message factory a logger was created with is the same as the given messageFactory. If they are
     * different log a warning to the {@linkplain StatusLogger}. A null MessageFactory translates to the default
     * MessageFactory {@link #DEFAULT_MESSAGE_FACTORY_CLASS}.
     *
     * @param logger The logger to check
     * @param messageFactory The message factory to check.
     */
    public static void checkMessageFactory(final ExtendedLogger logger, final MessageFactory messageFactory) {
        final String name = logger.getName();
        final MessageFactory loggerMessageFactory = logger.getMessageFactory();
        if (messageFactory != null && !loggerMessageFactory.equals(messageFactory)) {
            StatusLogger.getLogger().warn(
                    "The Logger {} was created with the message factory {} and is now requested with the "
                            + "message factory {}, which may create log events with unexpected formatting.", name,
                    loggerMessageFactory, messageFactory);
        } else if (messageFactory == null && !loggerMessageFactory.getClass().equals(DEFAULT_MESSAGE_FACTORY_CLASS)) {
            StatusLogger
                    .getLogger()
                    .warn("The Logger {} was created with the message factory {} and is now requested with a null "
                            + "message factory (defaults to {}), which may create log events with unexpected "
                            + "formatting.",
                            name, loggerMessageFactory, DEFAULT_MESSAGE_FACTORY_CLASS.getName());
        }
    }

    private static Class<? extends MessageFactory> createClassForProperty(final String property,
            final Class<ReusableMessageFactory> reusableParameterizedMessageFactoryClass,
            final Class<ParameterizedMessageFactory> parameterizedMessageFactoryClass) {
        try {
            final String fallback = Constants.ENABLE_THREADLOCALS ? reusableParameterizedMessageFactoryClass.getName()
                    : parameterizedMessageFactoryClass.getName();
            final String clsName = PropertiesUtil.getProperties().getStringProperty(property, fallback);
            return LoaderUtil.loadClass(clsName).asSubclass(MessageFactory.class);
        } catch (final Throwable throwable) {
            return parameterizedMessageFactoryClass;
        }
    }

    private static Class<? extends FlowMessageFactory> createFlowClassForProperty(final String property,
            final Class<DefaultFlowMessageFactory> defaultFlowMessageFactoryClass) {
        try {
            final String clsName = PropertiesUtil.getProperties().getStringProperty(property, defaultFlowMessageFactoryClass.getName());
            return LoaderUtil.loadClass(clsName).asSubclass(FlowMessageFactory.class);
        } catch (final Throwable throwable) {
            return defaultFlowMessageFactoryClass;
        }
    }

    private static MessageFactory2 createDefaultMessageFactory() {
        try {
            final MessageFactory result = DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
            return narrow(result);
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MessageFactory2 narrow(final MessageFactory result) {
        if (result instanceof MessageFactory2) {
            return (MessageFactory2) result;
        }
        return new MessageFactory2Adapter(result);
    }

    private static FlowMessageFactory createDefaultFlowMessageFactory() {
        try {
            return DEFAULT_FLOW_MESSAGE_FACTORY_CLASS.newInstance();
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Logs entry to a method with location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param format The format String for the parameters.
     * @param paramSuppliers The parameters to the method.
     */
    @Deprecated
    protected EntryMessage enter(final String fqcn, final String format, final MessageSupplier... paramSuppliers) {
        EntryMessage entryMsg = null;
        if (isEnabled(Level.TRACE, ENTRY_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg = entryMsg(format, paramSuppliers), null);
        }
        return entryMsg;
    }

    /**
     * Logs entry to a method with location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param format The format String for the parameters.
     * @param params The parameters to the method.
     */
    protected EntryMessage enter(final String fqcn, final String format, final Object... params) {
        EntryMessage entryMsg = null;
        if (isEnabled(Level.TRACE, ENTRY_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg = entryMsg(format, params), null);
        }
        return entryMsg;
    }

    /**
     * Logs entry to a method with location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param messageSupplier The Supplier of the Message.
     */
    @Deprecated
    protected EntryMessage enter(final String fqcn, final MessageSupplier messageSupplier) {
        EntryMessage message = null;
        if (isEnabled(Level.TRACE, ENTRY_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, message = flowMessageFactory.newEntryMessage(
                    messageSupplier.get()), null);
        }
        return message;
    }

    /**
     * Logs entry to a method with location information.
     *
     * @param fqcn
     *            The fully qualified class name of the <b>caller</b>.
     * @param message
     *            the Message.
     * @since 2.6
     */
    protected EntryMessage enter(final String fqcn, final Message message) {
        EntryMessage flowMessage = null;
        if (isEnabled(Level.TRACE, ENTRY_MARKER, (Object) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, flowMessage = flowMessageFactory.newEntryMessage(message),
                    null);
        }
        return flowMessage;
    }

    @Deprecated
    @Override
    public void entry() {
        entry(FQCN, (Object[]) null);
    }

    @Override
    public void entry(final Object... params) {
        entry(FQCN, params);
    }

    /**
     * Logs entry to a method with location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param params The parameters to the method.
     */
    protected void entry(final String fqcn, final Object... params) {
        if (isEnabled(Level.TRACE, ENTRY_MARKER, (Object) null, null)) {
            if (params == null) {
                logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg(null, (Supplier<?>[]) null), null);
            } else {
                logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg(null, params), null);
            }
        }
    }

    protected EntryMessage entryMsg(final String format, final Object... params) {
        final int count = params == null ? 0 : params.length;
        if (count == 0) {
            if (Strings.isEmpty(format)) {
                return flowMessageFactory.newEntryMessage(null);
            }
            return flowMessageFactory.newEntryMessage(new SimpleMessage(format));
        }
        if (format != null) {
            return flowMessageFactory.newEntryMessage(new ParameterizedMessage(format, params));
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("params(");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final Object parm = params[i];
            sb.append(parm instanceof Message ? ((Message) parm).getFormattedMessage() : String.valueOf(parm));
        }
        sb.append(')');
        return flowMessageFactory.newEntryMessage(new SimpleMessage(sb));
    }

    protected EntryMessage entryMsg(final String format, final MessageSupplier... paramSuppliers) {
        final int count = paramSuppliers == null ? 0 : paramSuppliers.length;
        final Object[] params = new Object[count];
        for (int i = 0; i < count; i++) {
            params[i] = paramSuppliers[i].get();
            params[i] = params[i] != null ? ((Message) params[i]).getFormattedMessage() : null;
        }
        return entryMsg(format, params);
    }

    protected EntryMessage entryMsg(final String format, final Supplier<?>... paramSuppliers) {
        final int count = paramSuppliers == null ? 0 : paramSuppliers.length;
        final Object[] params = new Object[count];
        for (int i = 0; i < count; i++) {
            params[i] = paramSuppliers[i].get();
            if (params[i] instanceof Message) {
                params[i] = ((Message) params[i]).getFormattedMessage();
            }
        }
        return entryMsg(format, params);
    }

    @Deprecated
    @Override
    public void exit() {
        exit(FQCN, (Object) null);
    }

    @Deprecated
    @Override
    public <R> R exit(final R result) {
        return exit(FQCN, result);
    }

    /**
     * Logs exiting from a method with the result and location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param <R> The type of the parameter and object being returned.
     * @param result The result being returned from the method call.
     * @return the return value passed to this method.
     */
    protected <R> R exit(final String fqcn, final R result) {
        if (isEnabled(Level.TRACE, EXIT_MARKER, (CharSequence) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, EXIT_MARKER, exitMsg(null, result), null);
        }
        return result;
    }

    /**
     * Logs exiting from a method with the result and location information.
     *
     * @param fqcn The fully qualified class name of the <b>caller</b>.
     * @param <R> The type of the parameter and object being returned.
     * @param result The result being returned from the method call.
     * @return the return value passed to this method.
     */
    protected <R> R exit(final String fqcn, final String format, final R result) {
        if (isEnabled(Level.TRACE, EXIT_MARKER, (CharSequence) null, null)) {
            logMessageSafely(fqcn, Level.TRACE, EXIT_MARKER, exitMsg(format, result), null);
        }
        return result;
    }

    protected Message exitMsg(final String format, final Object result) {
        if (result == null) {
            if (format == null) {
                return messageFactory.newMessage("Exit");
            }
            return messageFactory.newMessage("Exit: " + format);
        }
        if (format == null) {
            return messageFactory.newMessage("Exit with(" + result + ')');
        }
        return messageFactory.newMessage("Exit: " + format, result);

    }

    @Override
    public EntryMessage traceEntry() {
        return enter(FQCN, null, (Object[]) null);
    }

    @Override
    public EntryMessage traceEntry(final String format, final Object... params) {
        return enter(FQCN, format, params);
    }

    @Override
    public EntryMessage traceEntry(final Supplier<?>... paramSuppliers) {
        return enter(FQCN, null, paramSuppliers);
    }

    @Override
    public EntryMessage traceEntry(final String format, final Supplier<?>... paramSuppliers) {
        return enter(FQCN, format, paramSuppliers);
    }

    @Override
    public EntryMessage traceEntry(final Message message) {
        return enter(FQCN, message);
    }

    @Override
    public void traceExit() {
        exit(FQCN, null, null);
    }

    @Override
    public <R> R traceExit(final R result) {
        return exit(FQCN, null, result);
    }

    @Override
    public <R> R traceExit(final String format, final R result) {
        return exit(FQCN, format, result);
    }

    @Override
    public void traceExit(final EntryMessage message) {
        // If the message is null, traceEnter returned null because flow logging was disabled, we can optimize out calling isEnabled().
        if (message != null && isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
            logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, flowMessageFactory.newExitMessage(message), null);
        }
    }

    @Override
    public <R> R traceExit(final EntryMessage message, final R result) {
        // If the message is null, traceEnter returned null because flow logging was disabled, we can optimize out calling isEnabled().
        if (message != null && isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
            logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, flowMessageFactory.newExitMessage(result, message), null);
        }
        return result;
    }

    @Override
    public <R> R traceExit(final Message message, final R result) {
        // If the message is null, traceEnter returned null because flow logging was disabled, we can optimize out calling isEnabled().
        if (message != null && isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
            logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, flowMessageFactory.newExitMessage(result, message), null);
        }
        return result;
    }

    private void readObject(final ObjectInputStream s) throws ClassNotFoundException, IOException {
        s.defaultReadObject( );
        try {
            Field f = this.getClass().getSuperclass().getDeclaredField("logBuilder");
            f.setAccessible(true);
            f.set(this, new LocalLogBuilder(this));
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            StatusLogger.getLogger().warn("Unable to initialize LogBuilder");
        }
    }
}
