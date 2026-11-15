package org.json;
import java.util.*;
public class JSONArray {
  final ArrayList<Object> list = new ArrayList<>();
  public JSONArray() {}
  public JSONArray(String s) throws JSONException { Object o = new JSONObject.Parser(s).value(); if(!(o instanceof JSONArray)) throw new JSONException("not array"); list.addAll(((JSONArray)o).list); }
  public JSONArray put(Object v){ list.add(v); return this; }
  public int length(){ return list.size(); }
  public Object opt(int i){ return i>=0&&i<list.size()? list.get(i) : null; }
  public JSONObject optJSONObject(int i){ Object v=opt(i); return v instanceof JSONObject ? (JSONObject)v : null; }
  public JSONArray optJSONArray(int i){ Object v=opt(i); return v instanceof JSONArray ? (JSONArray)v : null; }
  public String optString(int i,String d){ Object v=opt(i); return v==null? d : String.valueOf(v); }
  public String toString(){ StringBuilder sb=new StringBuilder("["); for(int i=0;i<list.size();i++){ if(i>0)sb.append(','); sb.append(JSONObject.encode(list.get(i))); } return sb.append(']').toString(); }
}
