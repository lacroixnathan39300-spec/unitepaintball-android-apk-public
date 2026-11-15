package com.unitepaintball.app;
/** Même estimation que lib/gift-benefits.ts ; prix et réservation revalidés par le serveur. */
public final class GiftBookingPricing {
 private GiftBookingPricing(){}
 public static int discount(String kind,int value,boolean equipment,String restrictedOffer,String offer,int players,boolean person,int unitPrice,int equipmentQty,int equipmentPrice,int total){
  if(!restrictedOffer.isEmpty()&&!restrictedOffer.equals(offer))throw new IllegalArgumentException("Ce bon est réservé à une autre formule.");
  long discount=0;
  if("amount".equals(kind))discount=value;
  if("percent".equals(kind))discount=(long)total*value/100;
  if("free_players".equals(kind)||"three_plus_one".equals(kind)){
   if("free_players".equals(kind)&&players<value)throw new IllegalArgumentException("Nombre de joueurs inférieur aux parties offertes.");
   if(!person)throw new IllegalArgumentException("Ce bon nécessite une formule par personne.");
   if("three_plus_one".equals(kind)&&players<4)throw new IllegalArgumentException("L’offre 3 + 1 nécessite au moins 4 joueurs.");
   int count=Math.min(players,"three_plus_one".equals(kind)?1:value);discount=(long)count*unitPrice+(equipment?(long)Math.min(count,equipmentQty)*equipmentPrice:0);
  }
  return (int)Math.max(0,Math.min(total,discount));
 }
}
