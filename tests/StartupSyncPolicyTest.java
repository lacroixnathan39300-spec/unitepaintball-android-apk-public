import com.unitepaintball.app.StartupSyncPolicy;
public class StartupSyncPolicyTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
 check(!StartupSyncPolicy.hasCompletedSync(0));
 check(StartupSyncPolicy.hasCompletedSync(1));
 check(StartupSyncPolicy.ready(true,0,0));
 check(!StartupSyncPolicy.ready(false,0,0));
 check(!StartupSyncPolicy.ready(true,1,0));
 check(!StartupSyncPolicy.ready(true,0,1));
 check(!StartupSyncPolicy.failure("/api/admin-players",403));
 check(!StartupSyncPolicy.failure("/api/admin-players",404));
 check(StartupSyncPolicy.failure("/api/avatar/soldier",404));
 check(StartupSyncPolicy.failure("/api/platform",403));
 check(StartupSyncPolicy.failure("/api/platform",401));
 check(StartupSyncPolicy.failure("/api/content",503));
 System.out.println("PASS: 12 startup gate assertions (Java runtime)");
 }
}
