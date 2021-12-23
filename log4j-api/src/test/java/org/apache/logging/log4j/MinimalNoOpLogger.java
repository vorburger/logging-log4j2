package org.apache.logging.log4j;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.spi.AbstractMinimalLogger;
import org.apache.logging.log4j.spi.EntryExitNoOpLogger;

public class MinimalNoOpLogger extends AbstractMinimalLogger implements EntryExitNoOpLogger {

    // The purpose of this class is to make sure at compile time that AbstractMinimalLogger can be implemented with just 1 method:

    public MinimalNoOpLogger(String name) {
        super(name, new NoOpMessageFactory());
    }

    @Override public Level getLevel() {
        return Level.OFF;
    }

    @Override public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable t) { }

    private static class NoOpMessageFactory implements MessageFactory2 {

        @Override public Message newMessage(Object message) {
            return null;
        }

        @Override public Message newMessage(String message) {
            return null;
        }

        @Override public Message newMessage(String message, Object... params) {
            return null;
        }

        @Override public Message newMessage(CharSequence charSequence) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
                Object p7) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
                Object p7, Object p8) {
            return null;
        }

        @Override public Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
                Object p7, Object p8, Object p9) {
            return null;
        }
    }
}
