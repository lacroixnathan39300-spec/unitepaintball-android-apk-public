package com.unitepaintball.app;

import java.net.URI;
import java.net.URLDecoder;
import java.time.LocalDate;

/** Validation des saisies ; le serveur reste seul responsable des droits et de l'usage unique. */
public final class GiftCertificatePolicy {
    private GiftCertificatePolicy() {}
    public static String code(String input, String baseUrl) {
        String value=input==null?"":input.trim();
        if(value.matches("UP-[A-Fa-f0-9]{24}"))return value.toUpperCase(java.util.Locale.ROOT);
        try {
            URI u=new URI(value),base=new URI(baseUrl);
            if(!"https".equalsIgnoreCase(u.getScheme())||!base.getHost().equalsIgnoreCase(u.getHost())||u.getPort()!=base.getPort()||u.getUserInfo()!=null||!"/bon-cadeau".equals(u.getPath()))return "";
            for(String p:u.getRawQuery().split("&")) {
                String[] kv=p.split("=",2);
                if(kv.length==2&&"code".equals(kv[0])) {
                    String c=URLDecoder.decode(kv[1],"UTF-8");
                    return c.matches("UP-[A-Fa-f0-9]{24}")?c.toUpperCase(java.util.Locale.ROOT):"";
                }
            }
        }catch(Exception ignored){}
        return "";
    }
    public static void validateEffect(String kind,int value) {
        if("custom".equals(kind)||"three_plus_one".equals(kind))return;
        int max="percent".equals(kind)?100:"free_players".equals(kind)?40:100000;
        if(!("amount".equals(kind)||"percent".equals(kind)||"free_players".equals(kind))||value<1||value>max)
            throw new IllegalArgumentException("Valeur invalide pour cet avantage (1 à "+max+").");
    }
    public static void validateDate(String value) {
        if(value.isEmpty())return;
        try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}")||!LocalDate.parse(value).toString().equals(value))throw new IllegalArgumentException();}
        catch(Exception e){throw new IllegalArgumentException("Date invalide : utilise AAAA-MM-JJ ou laisse vide.");}
    }
}
