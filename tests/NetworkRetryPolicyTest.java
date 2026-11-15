import com.unitepaintball.app.NetworkRetryPolicy;
public class NetworkRetryPolicyTest {public static void main(String[] args){
 for(String method:new String[]{"POST","PATCH","DELETE"})for(int status:new int[]{0,502,503,504})check(!NetworkRetryPolicy.shouldRetry(method,0,status,true));
 check(NetworkRetryPolicy.shouldRetry("GET",0,0,true));for(int s:new int[]{502,503,504})check(NetworkRetryPolicy.shouldRetry("GET",0,s,false));
 for(int s:new int[]{200,400,401,403,404,409,500})check(!NetworkRetryPolicy.shouldRetry("GET",0,s,false));check(!NetworkRetryPolicy.shouldRetry("GET",1,503,true));System.out.println("GET recovery policy passed: retry once, no mutations/auth/conflict retries");}
 static void check(boolean ok){if(!ok)throw new AssertionError();}}
