package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.Supplier;

/**
 * A Logger which ignores traceEntry() and traceExit();
 */
public interface EntryExitNoOpLogger extends Logger {

    default EntryMessage traceEntry() {
        return null;
    }

    default EntryMessage traceEntry(String format, Object... params) {
        return null;
    }

    default EntryMessage traceEntry(Supplier<?>... paramSuppliers) {
        return null;
    }

    default EntryMessage traceEntry(String format, Supplier<?>... paramSuppliers) {
        return null;
    }

    default EntryMessage traceEntry(Message message) {
        return null;
    }

    default void traceExit() {
    }

    default <R> R traceExit(R result) {
        return result;
    }

    default <R> R traceExit(String format, R result) {
        return result;
    }

    default void traceExit(EntryMessage message) {
    }

    default <R> R traceExit(EntryMessage message, R result) {
        return result;
    }

    default <R> R traceExit(Message message, R result) {
        return result;
    }

    @Deprecated
    default void entry() {
    }

    @Deprecated
    default void entry(Object... params) {
    }

    @Deprecated
    default void exit() {
    }

    @Deprecated
    default <R> R exit(R result) {
        return result;
    }
}
