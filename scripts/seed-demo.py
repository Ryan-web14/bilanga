
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Peuplement de démonstration — Bilanga.

    python scripts/seed-demo.py --host https://bilanga-c65c6649bf37.herokuapp.com

Pourquoi un script d'API et non un script SQL
---------------------------------------------
La moitié de ce qui donne vie au logiciel n'est PAS saisissable : diagnostics,
recommandations, alertes, verdicts de santé de sonde, stades recalculés, codes
de parcelle. Ce sont des produits du pipeline. Des `INSERT` directs donneraient
des courbes et un système muet — le contraire d'une démonstration.

Tout passe donc par l'API, dans l'ordre où le système l'attend, et c'est
l'ingestion des relevés qui fabrique le reste.

Rejouable
---------
Chaque objet est cherché par son nom (ou son identifiant technique) avant d'être
créé. Relancer le script ne duplique rien — sauf les relevés, qui sont des faits
horodatés et non des entités nommées : passez --skip-readings pour les éviter.
"""

import argparse
import json
import math
import random
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone

# La console Windows est en cp1252 par défaut : sans cela, la première coche
# Unicode fait tomber le script avant même le premier appel.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

random.seed(20260731)  # séries reproductibles : deux exécutions donnent la même courbe

NOW = datetime.now(timezone.utc).replace(microsecond=0)
TODAY = NOW.date()

DEVICE_KEY = "bilanga-demo-device-key-soutenance-2026"
ADMIN_EMAIL = "admin@bilanga.cg"
ADMIN_PASSWORD = "Bilanga@Prod2026"

TOKEN = None
HOST = None
API = None


# ══════════════════════════════════════════════════════════════════════════
#  Transport
# ══════════════════════════════════════════════════════════════════════════

def call(method, path, body=None, device_key=False, quiet=False):
    """Un appel API. Rend le contenu de `data`, ou None sur échec."""
    url = path if path.startswith("http") else API + path
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=payload, method=method)
    req.add_header("Accept", "application/json")
    if payload:
        req.add_header("Content-Type", "application/json")
    if device_key:
        req.add_header("X-Device-Key", DEVICE_KEY)
    elif TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)

    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        if not quiet:
            try:
                msg = json.loads(raw).get("message", raw[:200])
            except Exception:
                msg = raw[:200]
            print(f"    ✗ {method} {path} → {e.code} : {msg}")
        return None
    except Exception as e:                                   # réseau, délai
        if not quiet:
            print(f"    ✗ {method} {path} → {e}")
        return None

    if not raw:
        return {}
    parsed = json.loads(raw)
    # /ingest/* répond sans enveloppe ; tout le reste est enveloppé.
    return parsed.get("data", parsed) if isinstance(parsed, dict) else parsed


def page(path):
    """Liste paginée → la liste nue (double imbrication `data.data`)."""
    d = call("GET", path)
    if not isinstance(d, dict):
        return d or []
    return d.get("data", []) if "pageable" in d else (d if isinstance(d, list) else [])


def login():
    global TOKEN
    d = call("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    if not d or not d.get("accessToken"):
        sys.exit("Connexion impossible. Le compte d'amorçage existe-t-il ?")
    TOKEN = d["accessToken"]
    print(f"  ✓ connecté — {ADMIN_EMAIL}")


# ══════════════════════════════════════════════════════════════════════════
#  Phase 1 — comptes
# ══════════════════════════════════════════════════════════════════════════

PEOPLE = [
    ("agronome@bilanga.cg",   "Aline",  "Nkodia",  "066112233", "AGRONOME"),
    ("technicien@bilanga.cg", "Patrick", "Mabiala", "066223344", "TECHNICIEN"),
    ("exploitant@bilanga.cg", "Joseph", "Bantsimba", "066334455", "EXPLOITANT"),
    ("conseiller@bilanga.cg", "Grâce",  "Loubaki",  "066445566", "AGRONOME"),
]


def seed_users():
    print("\n▸ Comptes")
    existing = {u["email"]: u for u in page("/admin/users?size=100")}
    out = {}
    for email, first, last, phone, role in PEOPLE:
        if email in existing:
            out[email] = existing[email]
            print(f"  · {email} — déjà présent")
            continue
        created = call("POST", "/admin/users", {
            "email": email, "firstname": first, "lastname": last, "phone": phone,
            "generatePassword": False, "password": "Bilanga@Demo2026",
            "roleNames": [role],
        })
        if created:
            out[email] = created
            print(f"  ✓ {email} ({role}) — mot de passe Bilanga@Demo2026")
    return out


# ══════════════════════════════════════════════════════════════════════════
#  Phase 2 — organisation
# ══════════════════════════════════════════════════════════════════════════

def seed_organization(users):
    print("\n▸ Organisation")
    owner = users.get("exploitant@bilanga.cg", {}).get("id")

    coops = page("/admin/cooperatives?size=50")
    coop = next((c for c in coops if c["name"] == "Coopérative de Makotipoko"), None)
    if not coop:
        coop = call("POST", "/admin/cooperatives", {
            "name": "Coopérative de Makotipoko",
            "location": "Makotipoko, Plateaux",
            "contactPhone": "066998877",
        })
    print(f"  ✓ coopérative {coop['id'] if coop else '—'}")

    farms = page("/admin/farms?size=50")
    farm = next((f for f in farms if f["name"] == "Exploitation Bilanga Nord"), None)
    if not farm:
        farm = call("POST", "/admin/farms", {
            "name": "Exploitation Bilanga Nord",
            "cooperativeId": coop["id"] if coop else None,
            "ownerUserId": owner,
            "location": "Makotipoko",
            "contactPhone": "066334455",
        })
    print(f"  ✓ exploitation {farm['id'] if farm else '—'}")

    if farm:
        for email, role in [("agronome@bilanga.cg", "CONSEILLER"),
                            ("technicien@bilanga.cg", "TECHNICIEN"),
                            ("conseiller@bilanga.cg", "CONSEILLER")]:
            uid = users.get(email, {}).get("id")
            if uid:
                call("POST", f"/admin/farms/{farm['id']}/members",
                     {"userId": uid, "role": role}, quiet=True)
        print("  ✓ membres attribués (conseiller, technicien)")
    return farm


# ══════════════════════════════════════════════════════════════════════════
#  Phase 3 — parcelles
# ══════════════════════════════════════════════════════════════════════════
#
# Chaque parcelle a une raison d'être. Sans ces quatre profils, la moitié des
# moteurs ne peut rien produire :
#   · deux parcelles géolocalisées à moins de 2 km  → moteur de VOISINAGE
#   · une parcelle PLUVIAL                          → IrrigationAdapter
#   · une parcelle sans coordonnées                 → dégradation propre (ni météo, ni voisinage)

PLOTS = [
    dict(key="nordest", name="Parcelle Nord-Est", location="Makotipoko — plateau",
         latitude=-2.7832, longitude=15.4211, altitude=320.0,
         soilType="ARGILEUX", irrigationType="GOUTTE_A_GOUTTE", area=1.5),
    dict(key="nord", name="Parcelle Nord", location="Makotipoko — bas-fond",
         latitude=-2.7901, longitude=15.4180, altitude=305.0,
         soilType="LIMONEUX", irrigationType="PLUVIAL", area=1.2),
    dict(key="sud", name="Parcelle Sud", location="Makotipoko — versant sud",
         latitude=-2.7935, longitude=15.4260, altitude=298.0,
         soilType="LIMONEUX", irrigationType="ASPERSION", area=0.9),
    dict(key="est", name="Parcelle Est", location="Piste de Ngabé",
         latitude=None, longitude=None, altitude=None,
         soilType="SABLEUX", irrigationType="MANUEL", area=2.1),
]


def seed_plots(users, farm):
    print("\n▸ Parcelles")
    owner = users.get("exploitant@bilanga.cg", {}).get("id")
    existing = {p["name"]: p for p in page("/plots?size=100")}
    out = {}
    for spec in PLOTS:
        body = {k: v for k, v in spec.items() if k != "key" and v is not None}
        body["userId"] = owner
        if farm:
            body["farmId"] = farm["id"]
        found = existing.get(spec["name"])
        if found:
            # PUT partiel : les champs omis sont préservés depuis la correction.
            plot = call("PUT", f"/plots/{found['id']}", body) or found
            print(f"  · {spec['name']} — mise à jour ({plot.get('plotCode')})")
        else:
            plot = call("POST", "/plots", body)
            print(f"  ✓ {spec['name']} — {plot.get('plotCode') if plot else 'échec'}")
        if plot:
            out[spec["key"]] = plot
    return out


# ══════════════════════════════════════════════════════════════════════════
#  Phase 4 — cultures
# ══════════════════════════════════════════════════════════════════════════
#
# Deux générations par parcelle là où c'est utile : une campagne CLOSE (avec son
# bilan figé) et la campagne en cours. Sans la première, /succession n'a rien à
# montrer et /compare-previous répond « première campagne ».

CROPS_PAST = [
    dict(plot="nord", cropName="TOMATE", variety="Roma", planted=-430, closed=-310,
         area=1.0, density=24000, lot="LOT-2025-A04", cycle=120,
         reason="RECOLTE_NORMALE",
         note="Campagne conforme. Deux rangs perdus à la grêle de juin.",
         costs=[("FERTILISATION", "Urée 46 %", 14.0, "kg/ha", 165000, -400),
                ("TRAITEMENT", "Bouillie bordelaise", 3.0, "kg/ha", 88000, -370),
                ("DESHERBAGE", None, None, None, 45000, -350)],
         harvests=[(1180.0, "kg", "BONNE", -318, 480), (410.0, "kg", "MOYENNE", -312, 430)]),
    dict(plot="sud", cropName="TOMATE", variety="Marmande", planted=-395, closed=-280,
         area=0.7, density=25000, lot="LOT-2025-B11", cycle=115,
         reason="RECOLTE_ANTICIPEE",
         note="Récolte avancée de dix jours devant une menace de mildiou.",
         costs=[("FERTILISATION", "NPK 15-15-15", 20.0, "kg/ha", 142000, -365),
                ("IRRIGATION", None, None, None, 36000, -330)],
         harvests=[(690.0, "kg", "BONNE", -284, 500)]),
]

CROPS_CURRENT = [
    dict(plot="nordest", cropName="TOMATE", variety="Roma", planted=-101,
         area=0.8, density=25000, lot="LOT-2026-A17", cycle=120),
    dict(plot="nord", cropName="TOMATE", variety="Marmande", planted=-62,
         area=1.0, density=24000, lot="LOT-2026-B03", cycle=120),
    dict(plot="sud", cropName="TOMATE", variety="Roma", planted=-31,
         area=0.7, density=25000, lot="LOT-2026-C09", cycle=120),
    dict(plot="est", cropName="MANIOC", variety="Mvuazi", planted=-214,
         area=1.8, density=10000, lot="LOT-2025-M02", cycle=330),
]


def _iso_date(offset):
    return (TODAY + timedelta(days=offset)).isoformat()


def _instant(offset_days, hour=9):
    d = NOW + timedelta(days=offset_days)
    return d.replace(hour=hour, minute=0, second=0).isoformat().replace("+00:00", "Z")


def seed_past_campaigns(plots):
    """Campagne close + son bilan figé. À faire AVANT la campagne en cours :
    le gel du bilan porte sur la fenêtre plantation → aujourd'hui, et compterait
    sinon les charges de la campagne suivante."""
    print("\n▸ Campagnes closes (pour /succession et /compare-previous)")
    for spec in CROPS_PAST:
        plot = plots.get(spec["plot"])
        if not plot:
            continue
        already = [c for c in page(f"/crops?plotId={plot['id']}&size=50")
                   if c.get("plantingDate") == _iso_date(spec["planted"])]
        if already:
            print(f"  · {plot['name']} — campagne {spec['variety']} déjà close")
            continue

        crop = call("POST", "/crops", {
            "plotId": plot["id"], "cropName": spec["cropName"], "variety": spec["variety"],
            "plantingDate": _iso_date(spec["planted"]), "cycleDurationDays": spec["cycle"],
            "plantedArea": spec["area"], "plantDensity": spec["density"],
            "seedLot": spec["lot"], "status": "EN_COURS",
        })
        if not crop:
            continue

        for typ, product, dose, unit, cost, when in spec["costs"]:
            call("POST", "/interventions", {
                "plotId": plot["id"], "cropId": crop["id"], "type": typ,
                "product": product, "dose": dose, "unit": unit,
                "cost": cost, "performedAt": _instant(when),
                "note": "Campagne précédente — saisie rétrospective.",
            }, quiet=True)

        for qty, unit, quality, when, price in spec["harvests"]:
            call("POST", "/harvests", {
                "plotId": plot["id"], "cropId": crop["id"], "quantity": qty, "unit": unit,
                "quality": quality, "harvestedAt": _iso_date(when),
                "unitPrice": price, "currency": "XAF",
            }, quiet=True)

        closed = call("POST", f"/crops/{crop['id']}/close", {
            "reason": spec["reason"], "actualEndDate": _iso_date(spec["closed"]),
            "note": spec["note"],
        })
        print(f"  ✓ {plot['name']} — {spec['variety']} close ({spec['reason']})"
              f"{' · bilan figé' if closed else ''}")


def seed_current_crops(plots):
    print("\n▸ Campagnes en cours")
    out = {}
    for spec in CROPS_CURRENT:
        plot = plots.get(spec["plot"])
        if not plot:
            continue
        body = {
            "plotId": plot["id"], "cropName": spec["cropName"], "variety": spec["variety"],
            "plantingDate": _iso_date(spec["planted"]), "cycleDurationDays": spec["cycle"],
            "plantedArea": spec["area"], "plantDensity": spec["density"],
            "seedLot": spec["lot"], "status": "EN_COURS",
        }
        active = [c for c in page(f"/crops?plotId={plot['id']}&size=50")
                  if c.get("status") == "EN_COURS"]
        if active:
            crop = call("PUT", f"/crops/{active[0]['id']}", body) or active[0]
            print(f"  · {plot['name']} — {crop.get('cropName')} recalée "
                  f"({crop.get('growthStage')})")
        else:
            crop = call("POST", "/crops", body)
            print(f"  ✓ {plot['name']} — {spec['cropName']} "
                  f"({crop.get('growthStage') if crop else 'échec'})")
        if crop:
            out[spec["plot"]] = crop
    return out


# ══════════════════════════════════════════════════════════════════════════
#  Phase 5 — boîtiers et sondes
# ══════════════════════════════════════════════════════════════════════════
#
# Deux boîtiers sur une même parcelle ne sont pas un luxe : sans voisin,
# SensorHealthAnalyzer ne peut détecter ni dérive ni décrochage. Seule la règle
# de la valeur figée reste applicable.

DEVICES = [
    ("nordest", "ESP32-PROD-01", "Boîtier nord-est principal", 78, 3.92, "1.4.2"),
    ("nordest", "ESP32-NE-02",   "Boîtier nord-est témoin",    64, 3.81, "1.4.2"),
    ("nord",    "ESP32-N-01",    "Boîtier bas-fond",           41, 3.68, "1.4.0"),
    ("sud",     "ESP32-S-01",    "Boîtier sud",                88, 4.01, "1.4.2"),
    ("est",     "ESP32-E-01",    "Boîtier est",                55, 3.74, "1.3.9"),
    ("est",     "ESP32-E-02",    "Boîtier est secondaire",     17, 3.41, "1.3.9"),
]

SENSOR_TYPES = ["HUMIDITE_SOL", "TEMPERATURE", "PH", "NPK", "LUMINOSITE"]


def seed_devices(plots):
    print("\n▸ Boîtiers et sondes")
    existing = {d["technicalId"]: d for d in page("/devices?size=100")}
    out = {}
    for plot_key, tech, label, battery, volt, fw in DEVICES:
        plot = plots.get(plot_key)
        if not plot:
            continue
        body = {"plotId": plot["id"], "technicalId": tech, "deviceName": label,
                "status": "ACTIVE", "batteryLevel": battery, "batteryVoltage": volt,
                "firmwareVersion": fw, "installedAt": _instant(-240)}
        found = existing.get(tech)
        device = call("PUT", f"/devices/{found['id']}", body) or found if found \
            else call("POST", "/devices", body)
        if not device:
            continue
        out[tech] = device

        already = len(page(f"/sensors?deviceId={device['id']}&size=50"))
        for t in SENSOR_TYPES[already:]:
            call("POST", "/sensors",
                 {"deviceId": device["id"], "sensorType": t, "status": "ACTIVE"},
                 quiet=True)
        print(f"  ✓ {tech} sur {plot['name']} — {len(SENSOR_TYPES)} sondes")
    return out


# ══════════════════════════════════════════════════════════════════════════
#  Phase 6 — la série historique  (le levier n°1)
# ══════════════════════════════════════════════════════════════════════════
#
# Sans profondeur temporelle, six fonctions sont vides : courbes, projection de
# tendance (≥4 relevés sur 6 h), dérive de sonde (fenêtre 12 h), effet d'une
# intervention (48 h avant ET après), chronologie, comparaison de campagnes.
#
# ⚠️ recordedAt est renseigné sur CHAQUE relevé. Sans lui la série s'écrase sur
# l'instant d'envoi : la courbe devient un point, et la régression n'a plus rien
# à régresser.

def daylight(hour):
    """Luminosité en lux — cloche diurne, nulle la nuit."""
    if hour < 6 or hour > 18:
        return 0.0
    return round(26000 * math.sin(math.pi * (hour - 6) / 12), 0)


def air_temp(hour, base, amplitude):
    return round(base + amplitude * math.sin(math.pi * max(0.0, (hour - 5)) / 14), 1)


def build_series(tech, days, per_day, shape):
    """Génère les relevés d'un boîtier. `shape(progress, hour)` rend les mesures."""
    out = []
    step = 24 // per_day
    total = days * per_day
    i = 0
    for d in range(days, 0, -1):
        for slot in range(per_day):
            hour = (slot * step + 1) % 24
            when = (NOW - timedelta(days=d)).replace(
                hour=hour, minute=random.randint(0, 59), second=0)
            progress = i / max(1, total - 1)
            r = {"technicalId": tech, "quality": "SIMULEE",
                 "recordedAt": when.isoformat().replace("+00:00", "Z"),
                 "luminosite": daylight(hour), "signalStrength": -60 - random.randint(0, 25)}
            r.update(shape(progress, hour))
            out.append(r)
            i += 1
    return out


def jitter(v, amp):
    return round(v + random.uniform(-amp, amp), 1)


# ── Nord-Est : la parcelle vedette. Assèchement progressif jusqu'au stress
#    hydrique, puis remontée de l'humidité de l'air en fin de série — les deux
#    conditions du mildiou. C'est elle qui porte la démonstration.
def shape_nordest(p, hour):
    hum_sol = 58 - 36 * (p ** 2.1)                       # 58 % → 22 %
    hum_air = 66 + (26 * max(0.0, p - 0.72) / 0.28)      # bond à 92 % sur les 3 derniers jours
    return {
        "temperature": air_temp(hour, 22.5, 9.5),
        "temperatureSol": jitter(23.5 + 2.5 * p, 0.4),
        "humiditeSol": max(8.0, jitter(hum_sol, 1.2)),
        "humiditeAir": min(97.0, jitter(hum_air, 2.0)),
        "ph": round(6.45 - 0.35 * p + random.uniform(-0.04, 0.04), 2),
        "azote": jitter(46 - 9 * p, 1.0),
        "phosphore": jitter(19 - 2 * p, 0.6),
        "potassium": jitter(32 - 5 * p, 0.8),
        "pluviometrie": 11.5 if (0.40 < p < 0.44) else 0.0,
        "conductiviteElectrique": round(1.15 + 0.3 * p, 2),
    }


# ── Le témoin de la même parcelle : mêmes conditions, sondes saines. Il sert de
#    référence à la comparaison entre voisins.
def shape_nordest_peer(p, hour):
    base = shape_nordest(p, hour)
    return {k: (jitter(v, 1.4) if isinstance(v, float) and k != "pluviometrie" else v)
            for k, v in base.items()}


# ── Nord : parcelle PLUVIAL en stress hydrique. On ne peut pas lui dire
#    « irriguez » — c'est là que l'IrrigationAdapter reformule.
def shape_nord(p, hour):
    return {
        "temperature": air_temp(hour, 23.0, 10.0),
        "temperatureSol": jitter(24.0 + 3 * p, 0.5),
        "humiditeSol": max(9.0, jitter(50 - 27 * p, 1.5)),
        "humiditeAir": jitter(62 + 8 * p, 3.0),
        "ph": round(6.2 + random.uniform(-0.05, 0.05), 2),
        "azote": jitter(38 - 4 * p, 1.2),
        "phosphore": jitter(16, 0.8),
        "potassium": jitter(27, 1.0),
        "pluviometrie": 0.0,
        "conductiviteElectrique": 0.95,
    }


# ── Sud : conditions saines mais fenêtre favorable à la maladie en fin de série
#    (air saturé, température douce). Géolocalisée à ~1,2 km de Nord-Est : c'est
#    ce couple qui rend le moteur de VOISINAGE démontrable.
def shape_sud(p, hour):
    hum_air = 70 + (20 * max(0.0, p - 0.78) / 0.22)
    return {
        "temperature": air_temp(hour, 20.5, 6.0),
        "temperatureSol": jitter(22.0, 0.5),
        "humiditeSol": jitter(53 - 5 * p, 2.0),
        "humiditeAir": min(96.0, jitter(hum_air, 2.5)),
        "ph": round(6.6 + random.uniform(-0.05, 0.05), 2),
        "azote": jitter(48, 1.5),
        "phosphore": jitter(21, 0.8),
        "potassium": jitter(35, 1.2),
        "pluviometrie": 6.0 if (0.85 < p < 0.90) else 0.0,
        "conductiviteElectrique": 1.30,
    }


# ── Est : manioc, sol sableux, carence azotée franche. Aucune coordonnée : ni
#    météo ni voisinage — et le système doit le dire plutôt que de le taire.
def shape_est(p, hour):
    return {
        "temperature": air_temp(hour, 24.0, 8.0),
        "temperatureSol": jitter(26.0, 0.6),
        "humiditeSol": jitter(43 - 4 * p, 1.8),
        "humiditeAir": jitter(58, 3.0),
        "ph": round(5.4 + random.uniform(-0.05, 0.05), 2),
        "azote": jitter(13 - 2 * p, 0.8),          # nettement sous le seuil
        "phosphore": jitter(9, 0.5),
        "potassium": jitter(22, 1.0),
        "pluviometrie": 0.0,
        "conductiviteElectrique": 0.60,
    }


def send_readings(readings, label, batch=25):
    """Envoi par lots. Le lot n'est pas atomique : un relevé fautif n'en fait
    pas perdre 199 autres."""
    ok = diagnosed = 0
    for i in range(0, len(readings), batch):
        chunk = readings[i:i + batch]
        res = call("POST", "/ingest/readings/batch", {"readings": chunk}, device_key=True)
        if res:
            ok += res.get("accepted", 0)
            diagnosed += res.get("diagnosed", 0)
            if res.get("rejected"):
                for f in res.get("failures", [])[:2]:
                    print(f"      ! {f.get('errorCode')} : {f.get('message')}")
        print(f"    … {label} {min(i + batch, len(readings))}/{len(readings)}",
              end="\r", flush=True)
    print(f"  ✓ {label} — {ok} relevés, {diagnosed} diagnostics" + " " * 20)
    return diagnosed


def seed_readings():
    print("\n▸ Séries historiques  (c'est ce qui fabrique diagnostics, conseils et alertes)")
    total = 0
    total += send_readings(build_series("ESP32-PROD-01", 12, 6, shape_nordest),
                           "Nord-Est (principal)")
    total += send_readings(build_series("ESP32-NE-02", 12, 2, shape_nordest_peer),
                           "Nord-Est (témoin)")
    total += send_readings(build_series("ESP32-N-01", 10, 4, shape_nord), "Nord (pluvial)")
    total += send_readings(build_series("ESP32-S-01", 10, 4, shape_sud), "Sud")
    total += send_readings(build_series("ESP32-E-01", 8, 3, shape_est), "Est (manioc)")

    # ── La sonde figée. Six relevés strictement identiques : une mesure physique
    #    varie toujours au moins sur sa dernière décimale, six fois de suite n'est
    #    plus un phénomène naturel. La valeur choisie est PLAUSIBLE — c'est
    #    précisément le cas dangereux, et la raison d'être de cette règle.
    frozen = []
    for d in range(4, 0, -1):
        for slot in range(3):
            when = (NOW - timedelta(days=d)).replace(hour=slot * 8 + 2, minute=15, second=0)
            frozen.append({"technicalId": "ESP32-E-02", "quality": "SIMULEE",
                           "recordedAt": when.isoformat().replace("+00:00", "Z"),
                           "temperature": 25.0, "temperatureSol": 26.0,
                           "humiditeSol": 41.0, "humiditeAir": 58.0, "ph": 5.4,
                           "azote": 13.0, "phosphore": 9.0, "potassium": 22.0,
                           "luminosite": 12000.0, "pluviometrie": 0.0,
                           "conductiviteElectrique": 0.6, "signalStrength": -88})
    send_readings(frozen, "Est (sonde figée → DEFAILLANTE)")
    return total


# ══════════════════════════════════════════════════════════════════════════
#  Phase 7 — interventions, récoltes, observations
# ══════════════════════════════════════════════════════════════════════════
#
# Les dates sont choisies pour que /interventions/{id}/effect ait 48 h de
# relevés de part et d'autre. Une intervention posée en bord de série ne
# produirait qu'un « INDETERMINE ».

INTERVENTIONS = [
    ("nordest", "IRRIGATION",    None,                  None, None,  22000, -6, True),
    ("nordest", "FERTILISATION", "Urée 46 %",           12.5, "kg/ha", 31000, -4, False),
    ("nord",    "DESHERBAGE",    None,                  None, None,  18000, -5, False),
    ("sud",     "TRAITEMENT",    "Bouillie bordelaise", 2.5,  "kg/ha", 27500, -3, False),
    ("est",     "FERTILISATION", "NPK 15-15-15",        18.0, "kg/ha", 44000, -5, False),
]


def seed_interventions(plots, crops, users):
    print("\n▸ Interventions  (dont une qui ferme la boucle conseil → action → effet)")
    actor = users.get("exploitant@bilanga.cg", {}).get("id")
    for plot_key, typ, product, dose, unit, cost, when, link in INTERVENTIONS:
        plot, crop = plots.get(plot_key), crops.get(plot_key)
        if not plot:
            continue
        body = {"plotId": plot["id"], "type": typ, "product": product, "dose": dose,
                "unit": unit, "cost": cost, "performedAt": _instant(when, hour=7),
                "performedById": actor, "weatherNote": "Ciel couvert, sol ressuyé",
                "note": "Saisie de démonstration."}
        if crop:
            body["cropId"] = crop["id"]

        # Rattacher un conseil réel : c'est ce rattachement qui bascule la
        # recommandation en APPLIQUEE et rend le taux de suivi non nul.
        if link:
            advice = [r for r in page(
                f"/recommendations?plotId={plot['id']}&status=ACTIVE&size=20")
                if r.get("priority") == "HAUTE"]
            if advice:
                body["recommendationId"] = advice[0]["id"]

        created = call("POST", "/interventions", body, quiet=True)
        if created:
            print(f"  ✓ {plot['name']} — {typ}"
                  + (" · conseil marqué APPLIQUEE" if body.get("recommendationId") else ""))


def seed_harvests(plots, crops):
    print("\n▸ Récoltes")
    rows = [("nordest", 520.0, "kg", "BONNE", -9, 520),
            ("nordest", 340.0, "kg", "EXCELLENTE", -3, 560),
            ("est", 1450.0, "kg", "MOYENNE", -12, 210)]
    for plot_key, qty, unit, quality, when, price in rows:
        plot, crop = plots.get(plot_key), crops.get(plot_key)
        if not (plot and crop):
            continue
        r = call("POST", "/harvests", {
            "plotId": plot["id"], "cropId": crop["id"], "quantity": qty, "unit": unit,
            "quality": quality, "harvestedAt": _iso_date(when),
            "unitPrice": price, "currency": "XAF",
            "note": "Pesée au champ.",
        }, quiet=True)
        if r:
            print(f"  ✓ {plot['name']} — {qty} {unit} à {price} XAF")


def seed_observations(plots, users):
    print("\n▸ Observations de terrain")
    actor = users.get("exploitant@bilanga.cg", {}).get("id")
    notes = [("nordest", "Taches brunes concentriques sur les feuilles basses, rang 4."),
             ("nordest", "Sol craquelé sur la moitié est de la parcelle."),
             ("nord", "Feuilles flétries en milieu de journée, turgescence retrouvée le soir."),
             ("sud", "Rosée persistante jusqu'à 10 h sous le couvert."),
             ("est", "Feuillage pâle, croissance ralentie depuis deux semaines.")]
    for plot_key, note in notes:
        plot = plots.get(plot_key)
        if plot:
            call("POST", "/observations",
                 {"plotId": plot["id"], "userId": actor, "note": note}, quiet=True)
    print(f"  ✓ {len(notes)} observations")


# ══════════════════════════════════════════════════════════════════════════
#  Phase 8 — itinéraire technique
# ══════════════════════════════════════════════════════════════════════════
#
# Le terme qui manquait : le système savait ce qui a été FAIT et ce qu'il
# CONSEILLE, jamais ce qui était PRÉVU. Les opérations sont datées en J+n —
# la seule forme qui survive au clonage.

ITINERARY = [
    ("SEMIS",         "Mise en place",                    3,  "LEVEE",          None, None, None, 45000),
    ("FERTILISATION", "Premier apport d'azote",          21,  "CROISSANCE",     "Urée 46 %", 10.0, "kg/ha", 28000),
    ("TRAITEMENT",    "Préventif entrée en floraison",   48,  "FLORAISON",      "Bouillie bordelaise", 2.5, "kg/ha", 27500),
    ("FERTILISATION", "Deuxième apport d'azote",         62,  "FRUCTIFICATION", "Urée 46 %", 12.5, "kg/ha", 31000),
    ("IRRIGATION",    "Appoint de fructification",       78,  "FRUCTIFICATION", None, None, None, 22000),
    ("DESHERBAGE",    "Dernier désherbage",              90,  "FRUCTIFICATION", None, None, None, 18000),
    ("RECOLTE",       "Récolte principale",             115,  "MATURATION",     None, None, None, None),
]


def seed_itinerary(crops):
    crop = crops.get("nordest")
    if not crop:
        return
    print("\n▸ Itinéraire technique (Parcelle Nord-Est)")
    if page(f"/crops/{crop['id']}/itinerary"):
        pass  # la route rend un objet, pas une liste — on tente et on ignore les doublons
    made = 0
    for typ, label, jn, stage, product, dose, unit, cost in ITINERARY:
        body = {"type": typ, "label": label, "daysAfterPlanting": jn,
                "growthStage": stage, "product": product, "dose": dose,
                "unit": unit, "estimatedCost": cost}
        if call("POST", f"/crops/{crop['id']}/itinerary", body, quiet=True):
            made += 1
    print(f"  ✓ {made} opérations planifiées — le rapprochement avec les "
          f"interventions réelles se calcule à la lecture")


# ══════════════════════════════════════════════════════════════════════════
#  Phase 9 — acheminement des notifications
# ══════════════════════════════════════════════════════════════════════════

def seed_preferences():
    print("\n▸ Préférences de notification (compte connecté)")
    r = call("PUT", "/notifications/preferences", {
        "minLevel": "MOYENNE", "channels": ["LOG", "EMAIL"], "language": "fr",
        "quietFromHour": 22, "quietToHour": 6,
    }, quiet=True)
    if r:
        print(f"  ✓ canaux {r.get('channels')} — disponibles : {r.get('availableChannels')}")


# ══════════════════════════════════════════════════════════════════════════
#  Bilan
# ══════════════════════════════════════════════════════════════════════════

def report():
    print("\n" + "═" * 64)
    print("  État de la démonstration")
    print("═" * 64)
    farm = call("GET", "/overview/farm")
    if farm:
        print(f"  parcelles          {farm.get('plotCount')}   {farm.get('plotsByStatus')}")
        print(f"  alertes ouvertes   {farm.get('openAlertCount')}   {farm.get('openAlertsByLevel')}")
        print(f"  boîtiers           {farm.get('deviceCount')}  "
              f"(batterie faible : {farm.get('lowBatteryDeviceCount')})")
    for label, path in [("relevés", "/readings?size=1"), ("diagnostics", "/diagnosis?size=1"),
                        ("conseils", "/recommendations?size=1"), ("alertes", "/alerts?size=1"),
                        ("interventions", "/interventions?size=1"), ("récoltes", "/harvests?size=1")]:
        d = call("GET", path)
        n = (d or {}).get("pageable", {}).get("totalElements", "?")
        print(f"  {label:<18} {n}")
    print("═" * 64)


def main():
    global HOST, API
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="https://bilanga-c65c6649bf37.herokuapp.com")
    ap.add_argument("--skip-readings", action="store_true",
                    help="ne rejoue pas les séries (elles ne sont pas idempotentes)")
    args = ap.parse_args()

    HOST = args.host.rstrip("/")
    API = HOST + "/sni/api/v1"
    print(f"Peuplement de {HOST}")

    login()
    users = seed_users()
    farm = seed_organization(users)
    plots = seed_plots(users, farm)
    seed_past_campaigns(plots)
    crops = seed_current_crops(plots)
    seed_devices(plots)
    if not args.skip_readings:
        seed_readings()
    seed_interventions(plots, crops, users)
    seed_harvests(plots, crops)
    seed_observations(plots, users)
    seed_itinerary(crops)
    seed_preferences()
    report()


if __name__ == "__main__":
    main()
