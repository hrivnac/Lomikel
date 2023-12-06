${graalvm_dir}/bin/native-image \
--initialize-at-build-time=\
org.apache.log4j.Level,\
org.apache.log4j.Logger,\
org.apache.log4j.helpers.LogLog,\
org.apache.log4j.Priority,\
org.apache.log4j.LogManager,\
org.apache.log4j.helpers.Loader,\
org.apache.log4j.Category,\
org.apache.log4j.spi.RootLogger,\
org.apache.log4j.spi.LoggingEvent,\
org.slf4j.LoggerFactory,\
org.slf4j.impl.Log4jLoggerAdapter,\
org.slf4j.impl.StaticLoggerBinder \
-H:Name=Lomikel.exe \
-H:Path=../bin \
-jar ../lib/Lomikel.exe.jar

#--allow-incomplete-classpath \
#--report-unsupported-elements-at-runtime \
#--no-fallback \
