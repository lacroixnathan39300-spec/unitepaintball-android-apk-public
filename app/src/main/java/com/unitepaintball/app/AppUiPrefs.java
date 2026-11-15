package com.unitepaintball.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Préférences d’interface. Depuis 2.2.36, les couleurs peuvent suivre le thème public du site ; aucune donnée serveur n’est modifiée depuis cette classe. */
public final class AppUiPrefs {
    private static final String PREFS = "up_settings";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_THEME = "app_theme";

    private static final Map<String,String> EN = new HashMap<>();
    private static final Map<String,String> DE = new HashMap<>();

    static {
        // Coque / navigation
        put("Menu principal","Main menu","Hauptmenü");
        put("Choisis une catégorie ou recherche directement un outil","Choose a category or search for a tool","Wähle eine Kategorie oder suche direkt nach einem Werkzeug");
        put("Rechercher dans le menu…","Search the menu…","Im Menü suchen…");
        put("Essentiel","Essentials","Wichtig");
        put("Accueil, espace joueur et alertes","Home, player area and alerts","Start, Spielerbereich und Hinweise");
        put("Accueil / Actualités","Home / News","Start / Neuigkeiten");
        put("Annonces, photos et informations","Announcements, photos and information","Ankündigungen, Fotos und Informationen");
        put("Espace joueur","Player area","Spielerbereich");
        put("Toutes mes informations essentielles","All my essential information","Alle wichtigen Informationen");
        put("Ma prochaine partie","My next game","Mein nächstes Spiel");
        put("Réservation, présence et accès rapide","Booking, attendance and quick access","Buchung, Anwesenheit und Schnellzugriff");
        put("Notifications","Notifications","Benachrichtigungen");
        put("Alertes et actions à effectuer","Alerts and actions to complete","Hinweise und ausstehende Aktionen");
        put("Réserver & venir","Book & visit","Buchen & kommen");
        put("Réservations, paiements et préparation","Bookings, payments and preparation","Buchungen, Zahlungen und Vorbereitung");
        put("Réservations","Bookings","Buchungen");
        put("Choisir une formule et une date","Choose a package and date","Paket und Datum wählen");
        put("Paiements","Payments","Zahlungen");
        put("Montants, acomptes et reste à payer","Amounts, deposits and balance due","Beträge, Anzahlungen und Restbetrag");
        put("Documents","Documents","Dokumente");
        put("Chartes, autorisations et justificatifs","Rules, permissions and supporting documents","Regeln, Genehmigungen und Nachweise");
        put("Avant de venir","Before you come","Vor dem Besuch");
        put("Checklist et préparation","Checklist and preparation","Checkliste und Vorbereitung");
        put("Mon QR code","My QR code","Mein QR-Code");
        put("Accès rapide à mon QR","Quick access to my QR code","Schnellzugriff auf meinen QR-Code");
        put("Liste d’attente","Waiting list","Warteliste");
        put("Sessions complètes et places libérées","Full sessions and released places","Volle Termine und frei gewordene Plätze");
        put("Adhésions","Memberships","Mitgliedschaften");
        put("Adhésion annuelle et avantages","Annual membership and benefits","Jahresmitgliedschaft und Vorteile");
        put("Communauté","Community","Community");
        put("Amis, groupes, messages et médias","Friends, groups, messages and media","Freunde, Gruppen, Nachrichten und Medien");
        put("Mes amis","My friends","Meine Freunde");
        put("Joueurs ajoutés et contacts","Added players and contacts","Hinzugefügte Spieler und Kontakte");
        put("Groupes et invitations","Groups and invitations","Gruppen und Einladungen");
        put("Équipes, groupes et invitations","Teams, groups and invitations","Teams, Gruppen und Einladungen");
        put("Messagerie","Messages","Nachrichten");
        put("Conversations privées et groupes","Private chats and groups","Private Chats und Gruppen");
        put("Galerie","Gallery","Galerie");
        put("Photos, vidéos et ajout direct","Photos, videos and direct upload","Fotos, Videos und direkter Upload");
        put("Jouer & progresser","Play & progress","Spielen & Fortschritt");
        put("Parties, événements, récompenses et scénarios","Games, events, rewards and scenarios","Spiele, Events, Belohnungen und Szenarien");
        put("Parties / Live","Games / Live","Spiele / Live");
        put("Direct, équipes, résultats et classement","Live, teams, results and rankings","Live, Teams, Ergebnisse und Rangliste");
        put("Événements","Events","Events");
        put("Voir les prochaines parties","See upcoming games","Kommende Spiele ansehen");
        put("Points et récompenses","Points and rewards","Punkte und Belohnungen");
        put("Badges, fidélité et classement","Badges, loyalty and ranking","Abzeichen, Treue und Rangliste");
        put("Tournois","Tournaments","Turniere");
        put("Compétitions et inscriptions","Competitions and registrations","Wettbewerbe und Anmeldungen");
        put("Jeux et scénarios","Games and scenarios","Spiele und Szenarien");
        put("Choisir parmi les missions","Choose from the missions","Mission auswählen");
        put("Sécurité","Safety","Sicherheit");
        put("Règlement et consignes","Rules and instructions","Regeln und Anweisungen");
        put("Compte & aide","Account & help","Konto & Hilfe");
        put("Profil, réglages et assistance","Profile, settings and support","Profil, Einstellungen und Hilfe");
        put("Mon profil","My profile","Mein Profil");
        put("Identité, avatar et préférences","Identity, avatar and preferences","Identität, Avatar und Einstellungen");
        put("Paramètres et confidentialité","Settings and privacy","Einstellungen und Datenschutz");
        put("Compte, notifications et application","Account, notifications and app","Konto, Benachrichtigungen und App");
        put("Aide et assistance","Help and support","Hilfe und Support");
        put("Guides, FAQ et signalement","Guides, FAQ and reporting","Anleitungen, FAQ und Meldungen");

        // Paramètres et actions communes
        put("PARAMÈTRES","SETTINGS","EINSTELLUNGEN");
        put("Paramètres","Settings","Einstellungen");
        put("Paramètres et confidentialité","Settings and privacy","Einstellungen und Datenschutz");
        put("Réglages réellement appliqués au compte et à l’application Android.","Settings actually applied to the account and Android app.","Einstellungen, die tatsächlich auf Konto und Android-App angewendet werden.");
        put("Notifications push sur ce compte","Push notifications on this account","Push-Benachrichtigungen für dieses Konto");
        put("Réservations et confirmations","Bookings and confirmations","Buchungen und Bestätigungen");
        put("Invitations et groupes","Invitations and groups","Einladungen und Gruppen");
        put("Messages","Messages","Nachrichten");
        put("Actualités / communication","News / communication","Neuigkeiten / Kommunikation");
        put("Enregistrer mes notifications","Save notification settings","Benachrichtigungseinstellungen speichern");
        put("Réglages son et vibration Android","Android sound and vibration settings","Android Ton- und Vibrationseinstellungen");
        put("Synchronisation et hors-ligne","Sync and offline","Synchronisierung und Offline");
        put("Vérification en arrière-plan","Background checks","Hintergrundprüfung");
        put("Cache local","Local cache","Lokaler Cache");
        put("Vider le cache téléchargé","Clear downloaded cache","Heruntergeladenen Cache leeren");
        put("Compte et confidentialité","Account and privacy","Konto und Datenschutz");
        put("Informations personnelles","Personal information","Persönliche Informationen");
        put("Consentements et documents","Consents and documents","Einwilligungen und Dokumente");
        put("Télécharger mes données","Download my data","Meine Daten herunterladen");
        put("Audit application / site","App / site audit","App-/Website-Audit");
        put("Langue et apparence","Language and appearance","Sprache und Darstellung");
        put("Langue Android","Android language","Android-Sprache");
        put("Thème","Theme","Design");
        put("Changer la langue","Change language","Sprache ändern");
        put("Changer le thème","Change theme","Design ändern");
        put("Push Firebase","Firebase push","Firebase Push");
        put("Configuré","Configured","Konfiguriert");
        put("À configurer","Needs configuration","Muss konfiguriert werden");
        put("Français","French","Französisch");
        put("Anglais","English","Englisch");
        put("Allemand","German","Deutsch");
        put("Sombre","Dark","Dunkel");
        put("Clair","Light","Hell");
        put("Annuler","Cancel","Abbrechen");
        put("Enregistrer","Save","Speichern");
        put("Modifier","Edit","Bearbeiten");
        put("Réessayer","Retry","Erneut versuchen");
        put("Fermer","Close","Schließen");
        put("Plus tard","Later","Später");
        put("Envoyer maintenant","Send now","Jetzt senden");
        put("Approuver","Approve","Genehmigen");
        put("Refuser","Reject","Ablehnen");
        put("Suspendre","Suspend","Sperren");
        put("Retour","Back","Zurück");
        put("← Retour à l’administration","← Back to administration","← Zurück zur Verwaltung");
        put("Ouvrir","Open","Öffnen");
        put("Ouvrir  →","Open  →","Öffnen  →");
        put("Ouvrir la gestion complète sur le site","Open full management on the site","Vollständige Verwaltung auf der Website öffnen");
        put("Aucune donnée","No data","Keine Daten");
        put("Chargement…","Loading…","Laden…");
        put("Chargement des données…","Loading data…","Daten werden geladen…");
        put("Synchronisation…","Syncing…","Synchronisierung…");

        // Administration
        put("Administration","Administration","Verwaltung");
        put("Tous les outils administrateur rangés par catégorie","All admin tools grouped by category","Alle Verwaltungswerkzeuge nach Kategorie");
        put("Tableau de bord","Dashboard","Übersicht");
        put("Vue générale et accès rapides","Overview and quick access","Übersicht und Schnellzugriff");
        put("Terrain","Field","Spielfeld");
        put("Scanner un QR","Scan a QR code","QR-Code scannen");
        put("Contrôle d’arrivée et présence","Arrival and attendance control","Ankunfts- und Anwesenheitskontrolle");
        put("Partie en direct","Live game","Live-Spiel");
        put("Score et arbitrage LIVE","LIVE score and refereeing","LIVE-Punktestand und Schiedsrichter");
        put("À valider","To review","Zu prüfen");
        put("Centre de validation","Review center","Prüfzentrum");
        put("Toutes les actions en attente","All pending actions","Alle ausstehenden Aktionen");
        put("Nouveaux arrivants","New accounts","Neue Konten");
        put("Valider ou refuser les comptes","Approve or reject accounts","Konten genehmigen oder ablehnen");
        put("Nouvelles réservations","New bookings","Neue Buchungen");
        put("Demandes de parties","Game requests","Spielanfragen");
        put("Modifications","Changes","Änderungen");
        put("Comparer avant et après","Compare before and after","Vorher und nachher vergleichen");
        put("Joueurs","Players","Spieler");
        put("Tous les joueurs","All players","Alle Spieler");
        put("Recherche et fiches","Search and profiles","Suche und Profile");
        put("Dossiers mineurs","Minor files","Minderjährigen-Akten");
        put("Autorisations parentales à vérifier","Parental permissions to review","Elterliche Genehmigungen prüfen");
        put("Absences et blocages","Absences and blocks","Abwesenheiten und Sperren");
        put("Compteurs, notes et réautorisation","Counters, notes and re-authorization","Zähler, Notizen und erneute Freigabe");
        put("À venir","Upcoming","Bevorstehend");
        put("Planning et gestion","Schedule and management","Planung und Verwaltung");
        put("Confirmations","Confirmations","Bestätigungen");
        put("À envoyer, en attente et reçues","To send, pending and received","Zu senden, ausstehend und erhalten");
        put("Historique","History","Verlauf");
        put("Réservations terminées","Completed bookings","Abgeschlossene Buchungen");
        put("Parties & tournois","Games & tournaments","Spiele & Turniere");
        put("Partie en cours","Current game","Laufendes Spiel");
        put("Sessions","Sessions","Termine");
        put("Prochaines parties et historique","Upcoming games and history","Kommende Spiele und Verlauf");
        put("Gestion des tournois","Tournament management","Turnierverwaltung");
        put("Création et inscriptions","Creation and registrations","Erstellung und Anmeldungen");
        put("Communications","Communications","Kommunikation");
        put("Centre de communication","Communication center","Kommunikationszentrum");
        put("Confirmations, rappels et historique","Confirmations, reminders and history","Bestätigungen, Erinnerungen und Verlauf");
        put("Push et préférences","Push and preferences","Push und Einstellungen");
        put("Gestion","Management","Verwaltung");
        put("Caisse hors ligne","Offline till","Offline-Kasse");
        put("Brouillons puis synchronisation","Drafts then sync","Entwürfe, danach Synchronisierung");
        put("Comptabilité","Accounting","Buchhaltung");
        put("Recettes, dépenses et exports","Income, expenses and exports","Einnahmen, Ausgaben und Exporte");
        put("Stocks","Stock","Bestand");
        put("Mouvements et alertes","Movements and alerts","Bewegungen und Warnungen");
        put("Matériel prêté","Loaned equipment","Verliehene Ausrüstung");
        put("Attribution et restitution","Assignment and return","Ausgabe und Rückgabe");
        put("Incidents / Premiers secours","Incidents / First aid","Vorfälle / Erste Hilfe");
        put("Fiches et suivi","Records and follow-up","Berichte und Nachverfolgung");
        put("Galerie / Actualités","Gallery / News","Galerie / Neuigkeiten");
        put("Photos, vidéos et publications","Photos, videos and posts","Fotos, Videos und Beiträge");
        put("Statistiques","Statistics","Statistiken");
        put("Fréquentation et résultats","Attendance and results","Besuche und Ergebnisse");
        put("Points et récompenses","Points and rewards","Punkte und Belohnungen");
        put("Règles et fidélité","Rules and loyalty","Regeln und Treue");
        put("Paramètres généraux","General settings","Allgemeine Einstellungen");
        put("Capacités et règles métier","Capacity and business rules","Kapazitäten und Geschäftsregeln");
        put("Historique administrateur","Admin history","Admin-Verlauf");
        put("Journal des actions","Action log","Aktionsprotokoll");
        put("Studio & sauvegardes","Studio & backups","Studio & Sicherungen");
        put("Sauvegardes","Backups","Sicherungen");
        put("Inventaire des données à protéger","Inventory of data to protect","Inventar der zu schützenden Daten");
        put("CONSULTATION","READ ONLY","NUR LESEN");
        put("SYNCHRONISATION","SYNC","SYNCHRONISIERUNG");
        put("ACTIONS RÉELLES","LIVE ACTIONS","ECHTE AKTIONEN");

        // Profil / champs communs
        put("Prénom","First name","Vorname");
        put("Nom","Last name","Nachname");
        put("Pseudo","Nickname","Spitzname");
        put("Téléphone","Phone","Telefon");
        put("Date de naissance","Date of birth","Geburtsdatum");
        put("Adresse","Address","Adresse");
        put("Code postal","Postal code","Postleitzahl");
        put("Ville","City","Stadt");
        put("Contact d'urgence","Emergency contact","Notfallkontakt");
        put("Personne à contacter en urgence","Emergency contact","Notfallkontakt");
        put("Niveau","Level","Niveau");
        put("Équipement","Equipment","Ausrüstung");
        put("Statut","Status","Status");
        put("Valeur","Value","Wert");
        put("Référence","Reference","Referenz");
        put("Créneau","Time slot","Zeitfenster");
        put("Joueurs","Players","Spieler");
        put("Réservation","Booking","Buchung");
        put("Confirmation","Confirmation","Bestätigung");
        put("E-mail","Email","E-Mail");
        put("Rôle","Role","Rolle");
        put("Membres","Members","Mitglieder");

        // Authentification / scanner / studio
        put("Connexion / inscription","Sign in / register","Anmelden / Registrieren");
        put("Crée ton compte sur le site puis l'application prendra automatiquement le relais.","Create your account on the site, then the app will automatically take over.","Erstelle dein Konto auf der Website; anschließend übernimmt die App automatisch.");
        put("●  Connexion sécurisée au site Unité Paintball","●  Secure connection to the Unité Paintball site","●  Sichere Verbindung zur Unité-Paintball-Website");
        put("Chargement de l'inscription / connexion…","Loading sign-in / registration…","Anmeldung / Registrierung wird geladen…");
        put("Une fois ton compte créé ou connecté, l'application s'ouvrira automatiquement.","Once your account is created or signed in, the app will open automatically.","Sobald dein Konto erstellt oder angemeldet ist, öffnet sich die App automatisch.");
        put("Connexion au site impossible. Vérifie Internet puis réessaie.","Unable to connect to the site. Check your internet connection and try again.","Verbindung zur Website nicht möglich. Prüfe deine Internetverbindung und versuche es erneut.");
        put("Compte reconnu. Ouverture de l'application…","Account recognized. Opening the app…","Konto erkannt. App wird geöffnet…");
        put("Scanner QR joueur","Scan player QR","Spieler-QR scannen");
        put("Prêt à scanner…","Ready to scan…","Bereit zum Scannen…");
        put("Scanner un QR","Scan a QR code","QR-Code scannen");
        put("Valider la présence","Confirm attendance","Anwesenheit bestätigen");
        put("Scanne le QR Unité Paintball","Scan the Unité Paintball QR code","Scanne den Unité-Paintball-QR-Code");
        put("Scan annulé.","Scan cancelled.","Scan abgebrochen.");
        put("Vérification du joueur…","Checking player…","Spieler wird geprüft…");
        put("QR non reconnu.","QR code not recognized.","QR-Code nicht erkannt.");
        put("QR reconnu · vérifie les informations avant validation.","QR recognized · check the information before confirming.","QR erkannt · prüfe die Angaben vor der Bestätigung.");
        put("DÉJÀ VALIDÉ","ALREADY CONFIRMED","BEREITS BESTÄTIGT");
        put("JOUEUR TROUVÉ","PLAYER FOUND","SPIELER GEFUNDEN");
        put("Groupe","Group","Gruppe");
        put("Prix attendu","Expected price","Erwarteter Preis");
        put("Valider la présence ?","Confirm attendance?","Anwesenheit bestätigen?");
        put("Valider","Confirm","Bestätigen");
        put("Éditeur visuel","Visual editor","Visueller Editor");
        put("Enregistrer","Save","Speichern");
        put("Automatisations","Automations","Automatisierungen");
        put("Pages","Pages","Seiten");
        put("Menus","Menus","Menüs");
        put("Formulaires","Forms","Formulare");
        put("Tarifs","Pricing","Preise");
        put("Historique","History","Verlauf");
        put("Aucun bloc sélectionné","No block selected","Kein Block ausgewählt");
        put("Touchez un bloc pour le régler","Tap a block to configure it","Tippe auf einen Block, um ihn einzustellen");
        put("Nom, type, sorties et actions du bloc apparaîtront ici.","The block name, type, outputs and actions will appear here.","Name, Typ, Ausgänge und Aktionen des Blocks erscheinen hier.");
        put("▶ Tester","▶ Test","▶ Testen");
        put("Brouillon","Draft","Entwurf");
        put("Publier","Publish","Veröffentlichen");
        put("Bibliothèque d’actions existantes","Existing action library","Bibliothek vorhandener Aktionen");
        put("Action personnalisée","Custom action","Benutzerdefinierte Aktion");
        put("Vérifier disponibilité","Check availability","Verfügbarkeit prüfen");
        put("Calculer tarif","Calculate price","Preis berechnen");
        put("Créer réservation","Create booking","Reservierung erstellen");
        put("Créer QR Code","Create QR code","QR-Code erstellen");
        put("Check-in joueur","Player check-in","Spieler-Check-in");
        put("Envoyer notification","Send notification","Benachrichtigung senden");
        put("Envoyer message","Send message","Nachricht senden");
        put("Ajouter points / badge","Add points / badge","Punkte / Abzeichen hinzufügen");
        put("Inscrire participation","Add participation","Teilnahme hinzufügen");
        put("Valider adhésion","Approve membership","Mitgliedschaft bestätigen");
        put("Mettre en liste d’attente","Add to waitlist","Zur Warteliste hinzufügen");
        put("Mettre à jour stock","Update stock","Bestand aktualisieren");
        put("Créer incident","Create incident","Vorfall erstellen");
        put("Action","Action","Aktion");
        put("Déclencheur","Trigger","Auslöser");
        put("Condition","Condition","Bedingung");
        put("Attente","Wait","Warten");
        put("Validation admin","Admin approval","Admin-Freigabe");
        put("Boucle","Loop","Schleife");
        put("Sous-flux","Sub-flow","Unterablauf");
        put("Variable","Variable","Variable");
        put("Télécharger le justificatif","Download supporting document","Beleg herunterladen");
        put("Type","Type","Typ");
        put("Écriture","Entry","Buchung");
        put("Aucun document","No document","Kein Dokument");
        put("Aucun reçu, facture ou avoir n’est actuellement enregistré dans la comptabilité du site.","No receipt, invoice or credit note is currently recorded in the site's accounting.","Derzeit ist kein Beleg, keine Rechnung oder Gutschrift in der Buchhaltung der Website erfasst.");
        put("La langue sélectionnée traduit la coque native, la navigation, les réglages et les libellés connus. Les textes métier dynamiques renvoyés par le site restent affichés tels que fournis par le serveur. Par défaut, les couleurs suivent le thème public du site. Les modes clair/sombre locaux restent des options de secours manuelles.","The selected language translates the native shell, navigation, settings and known labels. Dynamic business text returned by the site remains as provided by the server. The light/dark theme is applied locally to the native interface.","Die gewählte Sprache übersetzt die native Oberfläche, Navigation, Einstellungen und bekannte Bezeichnungen. Dynamische Fachtexte der Website bleiben in der vom Server gelieferten Sprache. Das helle/dunkle Design wird lokal auf die native Oberfläche angewendet.");
        put("Choisis les catégories enregistrées côté serveur. Le son détaillé de chaque canal reste réglable dans Android.","Choose the categories saved on the server. Detailed sound settings for each channel remain adjustable in Android.","Wähle die auf dem Server gespeicherten Kategorien. Die detaillierten Toneinstellungen jedes Kanals bleiben in Android einstellbar.");
        put("Synchronisation et hors-ligne","Sync and offline","Synchronisierung und Offline");
        put("Compte et confidentialité","Account and privacy","Konto und Datenschutz");
    }

    private AppUiPrefs() {}

    private static void put(String fr, String en, String de) {
        EN.put(fr, en);
        DE.put(fr, de);
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String language(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "fr");
    }

    public static void setLanguage(Context context, String value) {
        if (!"en".equals(value) && !"de".equals(value)) value = "fr";
        prefs(context).edit().putString(KEY_LANGUAGE, value).apply();
    }

    public static String theme(Context context) {
        return prefs(context).getString(KEY_THEME, "site");
    }

    public static void setTheme(Context context, String value) {
        if (!"light".equals(value) && !"dark".equals(value) && !"site".equals(value)) value = "site";
        prefs(context).edit().putString(KEY_THEME, value).apply();
    }

    public static boolean isLight(Context context) { return "light".equals(theme(context)); }
    public static boolean followsSiteTheme(Context context) { return "site".equals(theme(context)); }

    /** 2.2.36 : le thème site devient le comportement par défaut, y compris après mise à jour depuis l’ancien mode sombre. */
    public static void migrateToSiteThemeSync(Context context) {
        SharedPreferences p=prefs(context);
        if(p.getBoolean("site_theme_sync_migrated_236",false))return;
        String old=p.getString(KEY_THEME,"dark");
        if(!"light".equals(old)) old="site";
        p.edit().putString(KEY_THEME,old).putBoolean("site_theme_sync_migrated_236",true).apply();
    }

    public static String languageLabel(Context context) {
        String l = language(context);
        if ("en".equals(l)) return "English";
        if ("de".equals(l)) return "Deutsch";
        return "Français";
    }

    public static String themeLabel(Context context) {
        if (followsSiteTheme(context)) return "Site synchronisé";
        return isLight(context) ? t(context, "Clair") : t(context, "Sombre");
    }

    public static Locale locale(Context context) {
        String l = language(context);
        return "de".equals(l) ? Locale.GERMANY : "en".equals(l) ? Locale.UK : Locale.FRANCE;
    }

    static {
        put("Téléchargement","Downloading","Download");
        put("Vérification","Verifying","Überprüfung");
        put("Installation","Installing","Installation");
        put("Activé","Active","Aktiviert");
        put("Erreur","Error","Fehler");
        put("PACK BADGES 4K","4K BADGE PACK","4K-ABZEICHENPAKET");
        put("✓ Qualité 4K installée","✓ 4K quality installed","✓ 4K-Qualität installiert");
        put("Qualité 4K optionnelle","Optional 4K quality","Optionale 4K-Qualität");
        put("SUPPRIMER LE PACK 4K","REMOVE 4K PACK","4K-PAKET ENTFERNEN");
        put("TÉLÉCHARGER ET INSTALLER LE PACK 4K","DOWNLOAD AND INSTALL 4K PACK","4K-PAKET HERUNTERLADEN UND INSTALLIEREN");
        put("SUPPRESSION…","REMOVING…","WIRD ENTFERNT…");
        put("TÉLÉCHARGEMENT…","DOWNLOADING…","DOWNLOAD…");
        put("RÉESSAYER","RETRY","ERNEUT VERSUCHEN");
        put("24 badges existants · mêmes identifiants et mêmes déblocages · seuls les visuels passent en 4096 px.","24 existing badges · same IDs and unlocks · visuals upgraded to 4096 px.","24 bestehende Abzeichen · gleiche IDs und Freischaltungen · Bilder in 4096 px.");
    }

    public static String t(Context context, String french) {
        if (french == null || french.isEmpty()) return french;
        String lang = language(context);
        String out = "en".equals(lang) ? EN.get(french) : "de".equals(lang) ? DE.get(french) : null;
        return out == null ? french : out;
    }

    public static int bg(Context c) { return followsSiteTheme(c) ? NeonPalette.BG : isLight(c) ? Color.rgb(244,247,242) : NeonPalette.DEFAULT_BG; }
    public static int card(Context c) { return followsSiteTheme(c) ? NeonPalette.SURFACE : isLight(c) ? Color.WHITE : NeonPalette.DEFAULT_SURFACE; }
    public static int card2(Context c) { return followsSiteTheme(c) ? NeonPalette.SURFACE_ALT : isLight(c) ? Color.rgb(233,240,234) : NeonPalette.DEFAULT_SURFACE_ALT; }
    public static int primary(Context c) { return followsSiteTheme(c) ? NeonPalette.GREEN : isLight(c) ? Color.rgb(82,117,0) : NeonPalette.DEFAULT_GREEN; }
    public static int primaryText(Context c) { return followsSiteTheme(c) ? NeonPalette.PRIMARY_TEXT : isLight(c) ? Color.WHITE : NeonPalette.DEFAULT_BG_DEEP; }
    public static int orange(Context c) { return followsSiteTheme(c) ? NeonPalette.CYAN : isLight(c) ? Color.rgb(0,132,148) : NeonPalette.DEFAULT_CYAN; }
    public static int orangeDark(Context c) { return followsSiteTheme(c) ? darken(NeonPalette.CYAN,.55f) : isLight(c) ? Color.rgb(0,92,104) : Color.rgb(14,119,132); }
    public static int text(Context c) { return followsSiteTheme(c) ? NeonPalette.TEXT : isLight(c) ? Color.rgb(17,32,24) : NeonPalette.DEFAULT_TEXT; }
    public static int muted(Context c) { return followsSiteTheme(c) ? NeonPalette.MUTED : isLight(c) ? Color.rgb(88,105,96) : NeonPalette.DEFAULT_MUTED; }
    public static int stroke(Context c) { return followsSiteTheme(c) ? NeonPalette.STROKE : isLight(c) ? Color.rgb(198,211,201) : Color.argb(26,255,255,255); }
    private static int darken(int c,float f){return Color.rgb(Math.round(Color.red(c)*f),Math.round(Color.green(c)*f),Math.round(Color.blue(c)*f));}
    private static boolean lightChrome(Context c){
        int v=bg(c);double y=.299*Color.red(v)+.587*Color.green(v)+.114*Color.blue(v);return y>165;
    }


    /**
     * Respecte réellement les barres système sur Android 15/16 (edge-to-edge).
     * Le contenu reste sous la barre d'état et au-dessus de la barre de navigation,
     * sans dépendre d'une hauteur de notch ou d'un modèle de téléphone.
     */
    public static void applySafeArea(View root) {
        if (root == null) return;
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = Math.max(0, insets.getSystemWindowInsetLeft());
            int top = Math.max(0, insets.getSystemWindowInsetTop());
            int right = Math.max(0, insets.getSystemWindowInsetRight());
            int bottom = Math.max(0, insets.getSystemWindowInsetBottom());
            v.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    public static void applyWindow(Activity activity) {
        boolean light = followsSiteTheme(activity) ? lightChrome(activity) : isLight(activity);
        int bg = bg(activity);
        activity.getWindow().setStatusBarColor(bg);
        activity.getWindow().setNavigationBarColor(bg);
        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
