package org.json;
import java.util.*;
/** Minimal org.json replacement for offline harness only (not shipped). */
public class JSONObject {
  final LinkedHashMap<String,Object> m = new LinkedHashMap<>();
  public JSONObject() {}
  public JSONObject(String s) throws JSONException { Object o = new Parser(s).value(); if(!(o instanceof JSONObject)) throw new JSONException("not object"); m.putAll(((JSONObject)o).m); }
  public JSONObject put(String k, Object v) throws JSONException { if(k==null) throw new JSONException("null key"); m.put(k, v); return this; }
  public Object opt(String k){ return m.get(k); }
  public String optString(String k){ return optString(k,""); }
  public String optString(String k,String d){ Object v=m.get(k); return v==null||v==JSONObject.NULL? d : String.valueOf(v); }
  public boolean optBoolean(String k){ Object v=m.get(k); return v instanceof Boolean && (Boolean)v; }
  public boolean optBoolean(String k,boolean d){ Object v=m.get(k); return v instanceof Boolean ? (Boolean)v : d; }
  public long optLong(String k){ return optLong(k,0L); }
  public long optLong(String k,long d){ Object v=m.get(k); return v instanceof Number ? ((Number)v).longValue() : d; }
  public int optInt(String k){ return optInt(k,0); }
  public int optInt(String k,int d){ Object v=m.get(k); return v instanceof Number ? ((Number)v).intValue() : d; }
  public double optDouble(String k,double d){ Object v=m.get(k); return v instanceof Number ? ((Number)v).doubleValue() : d; }
  public JSONObject optJSONObject(String k){ Object v=m.get(k); return v instanceof JSONObject ? (JSONObject)v : null; }
  public JSONArray optJSONArray(String k){ Object v=m.get(k); return v instanceof JSONArray ? (JSONArray)v : null; }
  public Iterator<String> keys(){ return new ArrayList<>(m.keySet()).iterator(); }
  public int length(){ return m.size(); }
  public boolean has(String k){ return m.containsKey(k); }
  public static final Object NULL = new Object(){ public String toString(){return "null";} };
  public String toString(){ StringBuilder sb=new StringBuilder("{"); boolean first=true; for(Map.Entry<String,Object> e:m.entrySet()){ if(!first)sb.append(','); first=false; sb.append(quote(e.getKey())).append(':').append(encode(e.getValue())); } return sb.append('}').toString(); }
  static String encode(Object v){ if(v==null||v==NULL) return "null"; if(v instanceof String) return quote((String)v); return v.toString(); }
  static String quote(String s){ StringBuilder sb=new StringBuilder("\""); for(char c:s.toCharArray()){ switch(c){case '"':sb.append("\\\"");break;case '\\':sb.append("\\\\");break;case '\n':sb.append("\\n");break;case '\r':sb.append("\\r");break;case '\t':sb.append("\\t");break;default: if(c<0x20) sb.append(String.format("\\u%04x",(int)c)); else sb.append(c);} } return sb.append('"').toString(); }
  static class Parser { final String s; int i=0; Parser(String s){this.s=s;}
    void ws(){ while(i<s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    Object value() throws JSONException { ws(); if(i>=s.length()) throw new JSONException("eof"); char c=s.charAt(i);
      if(c=='{'){ i++; JSONObject o=new JSONObject(); ws(); if(s.charAt(i)=='}'){i++;return o;} while(true){ ws(); String k=(String)value(); ws(); expect(':'); Object v=value(); o.m.put(k,v); ws(); if(s.charAt(i)==','){i++;continue;} expect('}'); return o; } }
      if(c=='['){ i++; JSONArray a=new JSONArray(); ws(); if(s.charAt(i)==']'){i++;return a;} while(true){ a.list.add(value()); ws(); if(s.charAt(i)==','){i++;continue;} expect(']'); return a; } }
      if(c=='"') return str();
      if(s.startsWith("true",i)){i+=4;return Boolean.TRUE;} if(s.startsWith("false",i)){i+=5;return Boolean.FALSE;} if(s.startsWith("null",i)){i+=4;return JSONObject.NULL;}
      int st=i; while(i<s.length() && "+-0123456789.eE".indexOf(s.charAt(i))>=0) i++; if(st==i) throw new JSONException("bad token at "+i); String n=s.substring(st,i);
      if(n.contains(".")||n.contains("e")||n.contains("E")) return Double.parseDouble(n); return Long.parseLong(n); }
    void expect(char c) throws JSONException { if(i>=s.length()||s.charAt(i)!=c) throw new JSONException("expected "+c+" at "+i); i++; }
    String str() throws JSONException { expect('"'); StringBuilder sb=new StringBuilder(); while(true){ char c=s.charAt(i++); if(c=='"') return sb.toString(); if(c=='\\'){ char e=s.charAt(i++); switch(e){case 'n':sb.append('\n');break;case 'r':sb.append('\r');break;case 't':sb.append('\t');break;case 'u':sb.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:sb.append(e);} } else sb.append(c);} }
  }
}
