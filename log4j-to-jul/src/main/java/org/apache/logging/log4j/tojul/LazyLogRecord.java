package org.apache.logging.log4j.tojul;

import static jdk.internal.logger.SurrogateLogger.isFilteredFrame;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Extension of {@link java.util.logging.LogRecord} with lazy get source related methods.
 *
 * @author <a href="http://www.vorburger.ch">Michael Vorburger.ch</a> for Google
 */
/* package-local */ final class LazyLogRecord extends LogRecord {

    private static final long serialVersionUID = 6798134264543826471L;

    // parent class LogRecord already has a needToInferCaller but it's private
    private transient boolean stillNeedToInferCaller = true;

    LazyLogRecord(Level level, String msg) {
        super(level, msg);
    }

    @Override
    public String getSourceClassName() {
        if (stillNeedToInferCaller) {
            inferCaller();
        }
        return super.getSourceClassName();
    }

    @Override
    public String getSourceMethodName() {
        if (stillNeedToInferCaller) {
            inferCaller();
        }
        return super.getSourceMethodName();
    }

    private void inferCaller() {
        // implementation inspired by parent class LogRecord.inferCaller()
        Optional<StackWalker.StackFrame> frame = new CallerFinder().get();
        frame.ifPresent(f -> {
            setSourceClassName(f.getClassName());
            setSourceMethodName(f.getMethodName());
        });
        stillNeedToInferCaller = false;
    }

    // CallerFinder is copy/pasted from parent class LogRecord (where it is only package-local)
    private static final class CallerFinder implements Predicate<StackWalker.StackFrame> {
        private static final StackWalker WALKER;
        static {
            final PrivilegedAction<StackWalker> action =
                    () -> StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
            WALKER = AccessController.doPrivileged(action);
        }

        Optional<StackWalker.StackFrame> get() {
            return WALKER.walk(s -> s.filter(this).findFirst());
        }

        private boolean lookingForLogger = true;

        @Override
        public boolean test(StackWalker.StackFrame t) {
            final String cname = t.getClassName();
            if (lookingForLogger) {
                lookingForLogger = !isLoggerImplFrame(cname);
                return false;
            }
            return !isFilteredFrame(t);
        }

        private boolean isLoggerImplFrame(String cname) {
            return cname.equals("java.util.logging.Logger") ||
                    cname.startsWith("sun.util.logging.PlatformLogger");
        }
    }
}
