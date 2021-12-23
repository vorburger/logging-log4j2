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
package org.apache.logging.log4j;

// TODO Think through more how to properly relate this with the original LogManager - probably with a java.util.ServiceLoader?
public class MinimalLogManager {

    /**
     * Log4j property to set to the fully qualified class name of a custom implementation of
     * {@link org.apache.logging.log4j.spi.LoggerContextFactory}.
     */
    public static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";

    /**
     * The name of the root Logger.
     */
    public static final String ROOT_LOGGER_NAME = "";

    /**
     * Prevents instantiation
     */
    protected MinimalLogManager() {
    }

    /**
     * Returns a Logger using the fully qualified name of the Class as the Logger name.
     *
     * @param clazz The Class whose name should be used as the Logger name. If null it will default to the calling
     *              class.
     * @return The Logger.
     * @throws UnsupportedOperationException if {@code clazz} is {@code null} and the calling class cannot be
     *                                       determined.
     */
    public static Logger getLogger(final Class<?> cls) {
        // copy/pasted from org.apache.logging.log4j.LogManager.getLogger(Class<?>)
        final String canonicalName = cls.getCanonicalName();
        return getLogger(canonicalName != null ? canonicalName : cls.getName());
    }

    /**
     * Returns a Logger using the fully qualified class name of the value as the Logger name.
     *
     * @param value The value whose class name should be used as the Logger name. If null the name of the calling class
     *              will be used as the logger name.
     * @return The Logger.
     * @throws UnsupportedOperationException if {@code value} is {@code null} and the calling class cannot be
     *                                       determined.
     */
    public static Logger getLogger(final Object value) {
        return getLogger(value.getClass());
    }

    /**
     * Returns a Logger with the specified name.
     *
     * @param name The logger name. If null the name of the calling class will be used.
     * @return The Logger.
     * @throws UnsupportedOperationException if {@code name} is {@code null} and the calling class cannot be determined.
     */
    public static Logger getLogger(final String name) {
        return null;
    }

    /**
     * Returns the root logger.
     *
     * @return the root logger, named {@link #ROOT_LOGGER_NAME}.
     */
    public static Logger getRootLogger() {
        return getLogger(ROOT_LOGGER_NAME);
    }
}
