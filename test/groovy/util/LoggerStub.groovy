package util

import org.ods.util.ILogger

class LoggerStub implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn = true
    private final Map clockStore = [ : ]
    private Object logger

    LoggerStub(logger) {
        this.logger = logger
    }

    void debug(String message) {
        logger.debug message
    }

    void info(String message) {
        logger.info message
    }

    void warn(String message) {
        info ("WARN: ${message}")
    }

    void debugClocked(String component, String message = null) {
        debug(timedCall(component, message))
    }

    void infoClocked(String component, String message = null) {
        info(timedCall(component, message))
    }

    void warnClocked(String component, String message = null) {
        warn(timedCall(component, message))
    }

    boolean getDebugMode () {
        debugOn
    }

    void startClocked(String component) {
        timedCall (component)
    }

    @SuppressWarnings(['GStringAsMapKey', 'UnnecessaryElseStatement'])
    private def timedCall (String component, String message = null) {
        if (!component) {
            throw new IllegalArgumentException ("Component can't be null!")
        }
        def startTime = clockStore.get("${component}")
        if (startTime) {
            def timeDuration = System.currentTimeMillis() - startTime
            return "[${component}] ${message ?: ''} " +
                    "(took ${timeDuration} ms)"
        } else {
            clockStore << ["${component}": System.currentTimeMillis()]
            return "[${component}] ${message ?: ''}"
        }
    }

}