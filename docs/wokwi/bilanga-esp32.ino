/*
 * BILANGA — simulateur de boîtier de terrain, pour Wokwi.
 *
 * Envoie un relevé au backend toutes les WINDOW_MS millisecondes.
 *
 * CE QU'IL FAUT SAVOIR AVANT DE LE LIRE
 *
 *   · technicalId : n'importe quelle valeur convient. Un identifiant inconnu
 *     n'est plus refusé — le backend ENREGISTRE le boîtier à son premier relevé
 *     et le rattache à la parcelle la plus récente. Vous pouvez donc dupliquer
 *     ce projet autant de fois que vous voulez sans rien enregistrer à la main.
 *     Prérequis : au moins UNE parcelle doit exister côté backend.
 *
 *   · Toutes les mesures sont FACULTATIVES. Débranchez une sonde, retirez la
 *     ligne correspondante : le relevé passe quand même. Le microservice
 *     d'inférence impute la valeur manquante et DÉGRADE sa confiance en
 *     proportion — sous 0,60 le diagnostic devient non fiable et aucune alerte
 *     n'est levée. C'est voulu : ne rien conseiller plutôt que conseiller sur
 *     des chiffres fabriqués.
 *
 *   · Le régulateur écarte les diagnostics quand rien n'a bougé. Deux relevés
 *     identiques à cinq minutes d'intervalle donnent skipReason
 *     CONDITIONS_STABLES — ce n'est pas une erreur, c'est le comportement
 *     attendu. Le relevé, lui, est TOUJOURS enregistré.
 *
 *   · Six relevés STRICTEMENT identiques déclenchent la détection de sonde
 *     figée : sensorHealth passe DEFAILLANTE et le diagnostic est inhibé. Le
 *     bruit ajouté ci-dessous l'évite ; mettez JITTER à 0 pour l'exercer
 *     volontairement.
 *
 * MONTAGE WOKWI — aucun composant n'est requis : les valeurs sont simulées.
 * Pour brancher de vraies sondes, remplacez le corps de readSensors().
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

// ============================================================
// Configuration — les seules lignes à changer
// ============================================================

// Wokwi fournit un réseau ouvert nommé « Wokwi-GUEST », sans mot de passe.
static const char* WIFI_SSID     = "Wokwi-GUEST";
static const char* WIFI_PASSWORD = "";

static const char* BACKEND = "https://bilanga-c65c6649bf37.herokuapp.com";

// Clé d'ingestion. Valeur de démonstration ; remplacez-la si vous avez posé
// BILANGA_INGEST_DEVICE_KEY sur la plateforme.
static const char* DEVICE_KEY = "bilanga-demo-device-key-soutenance-2026";

// Identifiant du boîtier. CHANGEZ-LE pour chaque simulation que vous lancez en
// parallèle : c'est lui qui distingue deux boîtiers. Deux simulations partageant
// le même identifiant se marcheraient dessus, et la détection de sonde figée
// verrait des valeurs incohérentes venir du « même » appareil.
static const char* TECHNICAL_ID = "WOKWI-01";

// Intervalle d'émission. 60 s est un bon compromis en démonstration : assez
// lent pour lire les journaux, assez rapide pour ne pas attendre. Le régulateur
// de diagnostic travaille sur 5 minutes — en deçà, la plupart des relevés
// n'entraîneront pas de nouveau diagnostic, ce qui est normal.
static const unsigned long WINDOW_MS = 60000UL;

// Bruit ajouté à chaque mesure, en pourcentage. À 0, les valeurs deviennent
// strictement identiques d'un relevé à l'autre et la détection de sonde figée
// se déclenche au sixième — utile pour l'exercer, gênant sinon.
static const float JITTER = 0.03f;

// ============================================================

WiFiClientSecure client;

void setup() {
  Serial.begin(115200);
  delay(200);

  Serial.printf("\n[BILANGA] boîtier %s\n", TECHNICAL_ID);
  Serial.printf("[BILANGA] backend %s\n", BACKEND);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("[WiFi] connexion");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.printf(" ok, ip %s\n", WiFi.localIP().toString().c_str());

  // Wokwi n'embarque pas de magasin de certificats racine. setInsecure() lève
  // la vérification du certificat du serveur : acceptable en simulation, à
  // remplacer par client.setCACert(...) sur du matériel réel — sans quoi rien
  // ne distingue votre backend d'un intermédiaire qui s'y substituerait.
  client.setInsecure();

  randomSeed(esp_random());
}

/** Applique un bruit relatif, pour que deux relevés ne soient jamais identiques. */
float jitter(float value) {
  if (JITTER <= 0.0f) return value;
  float delta = value * JITTER;
  return value + (random(-1000, 1001) / 1000.0f) * delta;
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] perdu, reconnexion...");
    WiFi.reconnect();
    delay(2000);
    return;
  }

  // Valeurs simulées, choisies dans des plages agronomiquement plausibles pour
  // une tomate en conditions un peu sèches — de quoi obtenir un diagnostic
  // STRESS_HYDRIQUE et des recommandations, plutôt qu'un NORMAL sans intérêt
  // pour une démonstration.
  float temperature   = jitter(31.5f);   // air, en °C
  float temperatureSol = jitter(27.0f);  // sol — distincte de l'air
  float humiditeSol   = jitter(19.0f);   // %, basse : c'est ce qui déclenche
  float humiditeAir   = jitter(44.0f);   // %
  float ph            = jitter(6.4f);
  float azote         = jitter(24.0f);   // mg/kg
  float phosphore     = jitter(16.0f);
  float potassium     = jitter(28.0f);
  float luminosite    = jitter(22000.0f);// lux
  float pluviometrie  = 0.0f;            // mm
  float conductivite  = jitter(1.1f);

  // Construction manuelle du corps : ArduinoJson n'apporterait rien pour un
  // objet plat de quinze champs, et une dépendance de moins est une dépendance
  // de moins à installer dans Wokwi.
  //
  // ⚠️ recordedAt n'est PAS envoyé : le serveur horodate à la réception. Si
  //    vous rejouez une série après une coupure, ajoutez-le en ISO-8601 UTC —
  //    sans lui, toute la série s'écrase sur l'instant de reconnexion et
  //    l'analyse de tendance devient fausse.
  char body[640];
  snprintf(body, sizeof(body),
    "{\"technicalId\":\"%s\","
    "\"temperature\":%.2f,"
    "\"temperatureSol\":%.2f,"
    "\"humiditeSol\":%.2f,"
    "\"humiditeAir\":%.2f,"
    "\"ph\":%.2f,"
    "\"azote\":%.2f,"
    "\"phosphore\":%.2f,"
    "\"potassium\":%.2f,"
    "\"luminosite\":%.1f,"
    "\"pluviometrie\":%.2f,"
    "\"conductiviteElectrique\":%.2f,"
    "\"batteryLevel\":%d,"
    "\"batteryVoltage\":%.2f,"
    "\"signalStrength\":%d,"
    "\"firmwareVersion\":\"wokwi-1.0\"}",
    TECHNICAL_ID, temperature, temperatureSol, humiditeSol, humiditeAir,
    ph, azote, phosphore, potassium, luminosite, pluviometrie, conductivite,
    (int) random(70, 100), jitter(3.9f), (int) WiFi.RSSI());

  HTTPClient http;
  http.begin(client, String(BACKEND) + "/sni/api/v1/ingest/readings");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);

  // Le premier appel après une mise en veille du dyno réveille l'application :
  // comptez vingt à trente secondes. Un délai plus court ferait conclure à tort
  // que le backend est injoignable.
  http.setTimeout(40000);

  int status = http.POST(body);

  if (status > 0) {
    String response = http.getString();
    Serial.printf("[POST] %d\n%s\n", status, response.c_str());

    if (status == 401) {
      Serial.println(">> Clé d'ingestion refusée : vérifiez DEVICE_KEY.");
    } else if (status == 503) {
      Serial.println(">> Ingestion non configurée côté serveur "
                     "(bilanga.ingest.device-key vide).");
    } else if (status == 404) {
      Serial.println(">> Aucune parcelle n'existe pour accueillir ce boîtier. "
                     "Créez-en une : POST /sni/api/v1/plots");
    }
  } else {
    Serial.printf("[POST] échec réseau : %s\n",
                  http.errorToString(status).c_str());
  }

  http.end();
  delay(WINDOW_MS);
}
