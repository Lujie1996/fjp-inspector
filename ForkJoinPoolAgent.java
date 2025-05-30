import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinPool;

public class ForkJoinPoolAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        String corfuTableClassName = "org.corfudb.runtime.collections.Table";
        printCtlOfFJP(corfuTableClassName, "pool", inst);

        String fjpClassName = "java.util.concurrent.ForkJoinPool";
        printCtlOfFJP(fjpClassName, "common", inst);
    }

    private static void printCtlOfFJP(String className, String staticFieldName, Instrumentation inst) {
        try {
            // Get the current thread's context classloader
            // Try to load the class using the current thread's context classloader
            Class<?> targetClass = null;
            Thread currentThread = Thread.currentThread();
            ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

            try {
                targetClass = Class.forName(className, true, originalContextClassLoader);
            } catch (ClassNotFoundException e) {
                System.out.println("Class not found with context classloader: " + e.getMessage());
            }

            // If not found, iterate through all classloaders
            if (targetClass == null) {
                for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
                    ClassLoader cl = loadedClass.getClassLoader();
                    if (cl != null) {
                        try {
                            targetClass = Class.forName(className, true, cl);
                            System.out.println("Found class with classloader: " + cl);
                            break;
                        } catch (ClassNotFoundException ignored) {
                            // Continue trying other classloaders
                        }
                    }
                }
            }

            if (targetClass == null) {
                throw new ClassNotFoundException("Could not find class in any classloader");
            }

            // Get the static field from class
            Field targetField = targetClass.getDeclaredField(staticFieldName);
            targetField.setAccessible(true);

            // Get the ForkJoinPool instance
            ForkJoinPool pool = (ForkJoinPool) targetField.get(null);

            // Get the 'ctl' field from ForkJoinPool
            Field ctlField = ForkJoinPool.class.getDeclaredField("ctl");
            ctlField.setAccessible(true);

            // Get the value of 'ctl'
            long ctlValue = ctlField.getLong(pool);

            // Print the ctl value
            System.out.println("ForkJoinPool ctl value for " + className + "." + staticFieldName +
                    ": " + ctlAsBinary(ctlValue));

        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: " + e.getMessage());
        } catch (NoSuchFieldException e) {
            System.err.println("Field not found: " + e.getMessage());
        } catch (IllegalAccessException e) {
            System.err.println("Cannot access field: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    private static String ctlAsBinary(long value) {
        // convert and pad zeros
        String binaryCtl = String.format("%64s", Long.toBinaryString(value)).replace(' ', '0');
        String binaryRc = binaryCtl.substring(0, 16);
        String binaryTc = binaryCtl.substring(16, 32);
        String binarySs = binaryCtl.substring(32, 48);
        String binaryId = binaryCtl.substring(48, 64);

        return "CTL=(" + value + "), " +
                "RC=(" + prettifyBinary(binaryRc) + ", " + binaryToInt(binaryRc) + "), " +
                "TC=(" + prettifyBinary(binaryTc) + ", " + binaryToInt(binaryTc) + "), " +
                "SS=(" + prettifyBinary(binarySs) + ", " + binaryToInt(binarySs) + "), " +
                "ID=(" + prettifyBinary(binaryId) + ", " + binaryToInt(binaryId) + ")";
    }

    private static int binaryToInt(String binary) {
        int decimalValue = Integer.parseInt(binary, 2);

        // For negative value, subtract 2^16
        if (binary.charAt(0) == '1') {
            decimalValue -= (1 << 16);
        }
        return decimalValue;
    }

    // Add a space after 8 bits
    private static String prettifyBinary(String binary) {
        return binary.replaceAll("(.{8})", "$1 ");
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }
}