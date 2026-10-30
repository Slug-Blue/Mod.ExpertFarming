package bad;

// Every line below must be reported by ci/check-sources.sh.
public class Bad {
    Object a() throws Exception { return Runtime.getRuntime().exec("x"); }
    Object b() { return new ProcessBuilder("x"); }
    String c = "https://example.invalid/telemetry";
}
