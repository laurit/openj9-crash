package agent;

import java.lang.instrument.Instrumentation;

public class Agent {
  public static Instrumentation instrumentation;

  public static void premain(String agentArgs, Instrumentation inst) {
    instrumentation = inst;
  }
}
