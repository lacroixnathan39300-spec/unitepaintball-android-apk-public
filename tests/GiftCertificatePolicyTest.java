import com.unitepaintball.app.GiftCertificatePolicy;
public final class GiftCertificatePolicyTest {
 private static int checks;
 private static void check(boolean b){checks++;if(!b)throw new AssertionError("Test "+checks);}
 private static void bad(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 public static void main(String[] args){
  String base="https://unite-paintball-jura.lacroixnathan39300.chatgpt.site",code="UP-0123456789ABCDEF01234567";
  check(GiftCertificatePolicy.code(code,base).equals(code));
  check(GiftCertificatePolicy.code(base+"/bon-cadeau?code="+code,base).equals(code));
  check(GiftCertificatePolicy.code("https://evil.example/bon-cadeau?code="+code,base).isEmpty());
  check(GiftCertificatePolicy.code("http://"+base.substring(8)+"/bon-cadeau?code="+code,base).isEmpty());
  check(GiftCertificatePolicy.code(base+"/other?code="+code,base).isEmpty());
  check(GiftCertificatePolicy.code("UP-123456789ABC",base).isEmpty());
  check(GiftCertificatePolicy.code(base+"/bon-cadeau",base).isEmpty());
  GiftCertificatePolicy.validateDate("");GiftCertificatePolicy.validateDate("2028-02-29");
  bad(()->GiftCertificatePolicy.validateDate("2027-02-29"));bad(()->GiftCertificatePolicy.validateDate("07/10/2026"));
  GiftCertificatePolicy.validateEffect("free_players",40);bad(()->GiftCertificatePolicy.validateEffect("free_players",41));
  GiftCertificatePolicy.validateEffect("percent",100);bad(()->GiftCertificatePolicy.validateEffect("percent",101));
  bad(()->GiftCertificatePolicy.validateEffect("amount",0));bad(()->GiftCertificatePolicy.validateEffect("unknown",2));
  GiftCertificatePolicy.validateEffect("custom",0);GiftCertificatePolicy.validateEffect("three_plus_one",0);
  System.out.println(checks+" contrôles bons cadeaux OK");
 }
}
