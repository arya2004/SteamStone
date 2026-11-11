package steamstone.steam.nativeapi;

/**
 * Runtime contract shared by the application launcher, worker launcher, and native loader.
 */
public final class NativeRuntimeConstants {
    public static final String CHECK_RUNTIME_ARGUMENT = "--check-runtime";
    public static final int CHECK_RUNTIME_ARGUMENT_COUNT = 1;
    public static final int FIRST_ARGUMENT_INDEX = 0;
    public static final int PROTOCOL_ARGUMENT_COUNT = 2;
    public static final int RESPONSE_ARGUMENT_INDEX = 1;
    public static final String OS_NAME_PROPERTY = "os.name";
    public static final String JAVA_HOME_PROPERTY = "java.home";

}
