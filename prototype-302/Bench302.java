import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.engine.*;
import org.junit.platform.engine.support.descriptor.MethodSource;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import java.util.*;

/** PROTOTYPE (#302): ABBA rounds of two test classes; per test, its time and the Unified layer's phase times. */
public class Bench302 {
  public static void main(String[] a) throws Exception {
    int rounds = Integer.parseInt(a[0]);
    String A = a[1], B = a[2];
    java.lang.reflect.Method dump = Class.forName("com.vaadin.testing.unified.internal.Proto").getMethod("dump");
    Launcher l = LauncherFactory.create();
    Map<String,Long> start = new HashMap<>();
    List<String> out = new ArrayList<>();
    int[] round = {0};
    TestExecutionListener lis = new TestExecutionListener() {
      public void executionStarted(TestIdentifier t){ if(t.isTest()) { try { dump.invoke(null); } catch (Exception e) {} start.put(t.getUniqueId(), System.nanoTime()); } }
      public void executionFinished(TestIdentifier t, TestExecutionResult r){
        if(!t.isTest()) return;
        long d = System.nanoTime()-start.get(t.getUniqueId());
        String phases; try { phases = (String) dump.invoke(null); } catch (Exception e) { phases = "?"; }
        MethodSource m = (MethodSource) t.getSource().get();
        out.add(round[0]+","+m.getClassName().replace("demo.","")+","+m.getMethodName()+","+r.getStatus()+","+(d/1e6)+","+phases);
        if(r.getThrowable().isPresent()&&out.size()<4) r.getThrowable().get().printStackTrace();
      }
    };
    String tool = System.getProperty("vaadin.testing.tool", "browserless");
    for (int i=0;i<rounds;i++){
      round[0]=i;
      String[] order = (i%2==0)? new String[]{A,B} : new String[]{B,A};
      for (String c: order){
        LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(c))
          .configurationParameter("vaadin.testing.tool", tool)
          .configurationParameter("vaadin.testing.user-steps.log", "none")
          .configurationParameter("junit.jupiter.execution.parallel.enabled","false").build();
        l.execute(req, lis);
      }
    }
    for (String s: out) System.err.println("BENCH,"+s); System.exit(0);
  }
}
