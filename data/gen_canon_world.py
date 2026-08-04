#!/usr/bin/env python3
"""Generates canon-v2.tsv — the world canon.

Every row is validated against its country's bounding box before it is written,
so a transposed or mistyped coordinate fails the build rather than shipping.

Fields I can state with confidence are populated. `city` and `postal` are left
empty wherever filling them would mean inventing an address; the schema carries
them so a real source (Wikidata / OSM) can fill them later without a migration.
"""
import sys

# Rough bounding boxes: (lat_min, lat_max, lng_min, lng_max). Deliberately
# generous — this catches transpositions and sign errors, not fine placement.
BBOX = {
    "US": (18.0, 72.0, -180.0, -65.0), "CA": (41.0, 84.0, -142.0, -52.0),
    "MX": (14.0, 33.0, -119.0, -86.0), "BR": (-34.0, 6.0, -74.0, -34.0),
    "PE": (-19.0, 0.0, -82.0, -68.0), "AR": (-56.0, -21.0, -74.0, -53.0),
    # Chile stretches to Rapa Nui, 3,500 km into the Pacific.
    "CL": (-56.0, -17.0, -110.0, -66.0), "GB": (49.0, 61.0, -9.0, 2.0),
    "FR": (41.0, 51.5, -5.5, 10.0), "ES": (35.5, 44.0, -19.0, 5.0),
    "PT": (32.0, 42.5, -32.0, -6.0), "IT": (35.0, 47.5, 6.0, 19.0),
    "GR": (34.0, 42.0, 19.0, 30.0), "DE": (47.0, 55.5, 5.5, 15.5),
    "AT": (46.0, 49.5, 9.5, 17.5), "CH": (45.5, 48.0, 5.5, 10.7),
    "NL": (50.5, 54.0, 3.0, 7.5), "BE": (49.4, 51.6, 2.5, 6.5),
    "CZ": (48.5, 51.2, 12.0, 19.0), "HU": (45.7, 48.7, 16.0, 23.0),
    "PL": (49.0, 55.0, 14.0, 24.2), "HR": (42.3, 46.6, 13.4, 19.5),
    "NO": (57.0, 81.0, 4.0, 32.0), "SE": (55.0, 69.5, 10.5, 24.5),
    "IS": (63.0, 67.0, -25.0, -13.0), "IE": (51.3, 55.5, -11.0, -5.3),
    "TR": (35.8, 42.2, 25.6, 45.0), "EG": (22.0, 32.0, 24.0, 37.0),
    "MA": (27.5, 36.0, -13.5, -1.0), "ZA": (-35.0, -22.0, 16.0, 33.0),
    "TZ": (-12.0, -0.9, 29.0, 41.0), "KE": (-5.0, 5.5, 33.5, 42.0),
    "IN": (6.0, 36.0, 68.0, 98.0), "NP": (26.3, 30.5, 80.0, 88.3),
    "CN": (18.0, 54.0, 73.0, 135.0), "JP": (24.0, 46.0, 122.0, 146.0),
    "KR": (33.0, 39.0, 125.0, 130.0), "TH": (5.5, 20.5, 97.0, 106.0),
    "VN": (8.0, 23.5, 102.0, 110.0), "KH": (10.0, 15.0, 102.0, 108.0),
    "ID": (-11.0, 6.0, 95.0, 141.0), "AU": (-44.0, -10.0, 112.0, 154.0),
    "NZ": (-47.5, -34.0, 166.0, 179.0), "AE": (22.5, 26.5, 51.0, 56.5),
    "JO": (29.1, 33.4, 34.9, 39.3), "RU": (41.0, 78.0, 19.0, 180.0),
}

# (country, region_code, region_name, place_id, name, city, lat, lng, dwell_min, geofence_km, tags)
P = [
    # ---- France
    ("FR","FR-IDF","Île-de-France","fr-idf-eiffel","Eiffel Tower","Paris",48.8584,2.2945,60,2,"landmark|icon"),
    ("FR","FR-IDF","Île-de-France","fr-idf-louvre","Louvre Museum","Paris",48.8606,2.3376,120,2,"museum|art"),
    ("FR","FR-IDF","Île-de-France","fr-idf-versailles","Palace of Versailles","Versailles",48.8049,2.1204,120,3,"palace|unesco"),
    ("FR","FR-PAC","Provence-Alpes-Côte d'Azur","fr-pac-avignon","Palais des Papes","Avignon",43.9509,4.8076,90,2,"palace|unesco"),
    ("FR","FR-NOR","Normandy","fr-nor-montsaintmichel","Mont-Saint-Michel","Le Mont-Saint-Michel",48.6361,-1.5115,120,3,"abbey|unesco"),
    ("FR","FR-NAQ","Nouvelle-Aquitaine","fr-naq-lascaux","Lascaux Caves","Montignac",45.0537,1.1750,90,3,"prehistoric|unesco"),
    # ---- Italy
    ("IT","IT-LAZ","Lazio","it-laz-colosseum","Colosseum","Rome",41.8902,12.4922,90,2,"ruins|icon"),
    ("IT","IT-LAZ","Lazio","it-laz-vatican","St Peter's Basilica","Vatican City",41.9022,12.4539,90,2,"church|icon"),
    ("IT","IT-TOS","Tuscany","it-tos-florence","Florence Cathedral","Florence",43.7731,11.2560,90,2,"church|unesco"),
    ("IT","IT-TOS","Tuscany","it-tos-pisa","Leaning Tower of Pisa","Pisa",43.7230,10.3966,60,2,"landmark|icon"),
    ("IT","IT-VEN","Veneto","it-ven-stmarks","St Mark's Square","Venice",45.4341,12.3388,90,2,"square|unesco"),
    ("IT","IT-CAM","Campania","it-cam-pompeii","Pompeii","Pompei",40.7497,14.4869,150,4,"ruins|unesco"),
    ("IT","IT-CAM","Campania","it-cam-amalfi","Amalfi Coast","Amalfi",40.6340,14.6027,120,15,"coast|unesco"),
    # ---- Spain / Portugal
    ("ES","ES-CT","Catalonia","es-ct-sagrada","Sagrada Família","Barcelona",41.4036,2.1744,90,2,"church|icon"),
    ("ES","ES-CT","Catalonia","es-ct-parkguell","Park Güell","Barcelona",41.4145,2.1527,90,2,"park|unesco"),
    ("ES","ES-AN","Andalusia","es-an-alhambra","Alhambra","Granada",37.1761,-3.5881,150,3,"palace|unesco"),
    ("ES","ES-AN","Andalusia","es-an-mezquita","Mosque-Cathedral of Córdoba","Córdoba",37.8790,-4.7794,90,2,"church|unesco"),
    ("ES","ES-MD","Madrid","es-md-prado","Museo del Prado","Madrid",40.4138,-3.6921,120,2,"museum|art"),
    ("PT","PT-11","Lisbon","pt-11-belem","Belém Tower","Lisbon",38.6916,-9.2160,60,2,"tower|unesco"),
    ("PT","PT-11","Lisbon","pt-11-sintra","Pena Palace","Sintra",38.7876,-9.3907,120,3,"palace|unesco"),
    ("PT","PT-13","Porto","pt-13-ribeira","Ribeira","Porto",41.1407,-8.6130,90,2,"district|unesco"),
    # ---- United Kingdom / Ireland
    ("GB","GB-ENG","England","gb-eng-stonehenge","Stonehenge","Amesbury",51.1789,-1.8262,60,3,"prehistoric|unesco"),
    ("GB","GB-ENG","England","gb-eng-tower","Tower of London","London",51.5081,-0.0759,120,2,"castle|unesco"),
    ("GB","GB-ENG","England","gb-eng-britishmuseum","British Museum","London",51.5194,-0.1270,120,2,"museum"),
    ("GB","GB-SCT","Scotland","gb-sct-edinburgh","Edinburgh Castle","Edinburgh",55.9486,-3.1999,90,2,"castle"),
    ("GB","GB-SCT","Scotland","gb-sct-skye","Isle of Skye","Portree",57.4125,-6.1930,180,25,"island|landscape"),
    ("GB","GB-NIR","Northern Ireland","gb-nir-giants","Giant's Causeway","Bushmills",55.2408,-6.5116,90,3,"natural|unesco"),
    ("IE","IE-M","Munster","ie-m-cliffs","Cliffs of Moher","Liscannor",52.9719,-9.4265,90,3,"cliffs|natural"),
    ("IE","IE-L","Leinster","ie-l-trinity","Book of Kells, Trinity College","Dublin",53.3444,-6.2567,60,2,"library"),
    # ---- Germany / Austria / Switzerland / Czechia
    ("DE","DE-BY","Bavaria","de-by-neuschwanstein","Neuschwanstein Castle","Schwangau",47.5576,10.7498,120,3,"castle|icon"),
    ("DE","DE-BE","Berlin","de-be-brandenburg","Brandenburg Gate","Berlin",52.5163,13.3777,45,2,"landmark"),
    ("DE","DE-BE","Berlin","de-be-museumisland","Museum Island","Berlin",52.5169,13.4019,120,2,"museum|unesco"),
    ("AT","AT-9","Vienna","at-9-schonbrunn","Schönbrunn Palace","Vienna",48.1845,16.3122,120,3,"palace|unesco"),
    ("AT","AT-5","Salzburg","at-5-hallstatt","Hallstatt","Hallstatt",47.5622,13.6493,120,3,"village|unesco"),
    ("CH","CH-VS","Valais","ch-vs-matterhorn","Matterhorn","Zermatt",45.9763,7.6586,150,10,"mountain|icon"),
    ("CH","CH-BE","Bern","ch-be-jungfrau","Jungfraujoch","Lauterbrunnen",46.5474,7.9808,150,8,"mountain|unesco"),
    ("CZ","CZ-PR","Prague","cz-pr-charlesbridge","Charles Bridge","Prague",50.0865,14.4114,60,2,"bridge"),
    ("CZ","CZ-PR","Prague","cz-pr-castle","Prague Castle","Prague",50.0910,14.4016,120,2,"castle"),
    # ---- Netherlands / Belgium / Nordics
    ("NL","NL-NH","North Holland","nl-nh-rijks","Rijksmuseum","Amsterdam",52.3600,4.8852,120,2,"museum|art"),
    ("NL","NL-NH","North Holland","nl-nh-annefrank","Anne Frank House","Amsterdam",52.3752,4.8840,60,2,"museum|history"),
    ("BE","BE-BRU","Brussels","be-bru-grandplace","Grand-Place","Brussels",50.8467,4.3525,60,2,"square|unesco"),
    ("NO","NO-46","Vestland","no-46-geiranger","Geirangerfjord","Geiranger",62.1010,7.0060,150,15,"fjord|unesco"),
    ("NO","NO-03","Oslo","no-03-vigeland","Vigeland Park","Oslo",59.9270,10.6996,90,2,"park|art"),
    ("SE","SE-AB","Stockholm","se-ab-vasa","Vasa Museum","Stockholm",59.3280,18.0915,90,2,"museum"),
    ("IS","IS-1","Capital Region","is-1-bluelagoon","Blue Lagoon","Grindavík",63.8804,-22.4495,120,3,"geothermal"),
    ("IS","IS-8","Southern Region","is-8-jokulsarlon","Jökulsárlón","Höfn",64.0784,-16.2306,90,5,"glacier|natural"),
    # ---- Greece / Turkey / Croatia / Hungary / Poland
    ("GR","GR-A1","Attica","gr-a1-acropolis","Acropolis of Athens","Athens",37.9715,23.7267,120,2,"ruins|unesco"),
    ("GR","GR-L","South Aegean","gr-l-santorini","Oia, Santorini","Oia",36.4618,25.3753,120,3,"village|coast"),
    ("GR","GR-K","Central Macedonia","gr-k-meteora","Meteora","Kalambaka",39.7217,21.6306,150,8,"monastery|unesco"),
    ("TR","TR-34","Istanbul","tr-34-hagiasophia","Hagia Sophia","Istanbul",41.0086,28.9802,90,2,"church|unesco"),
    ("TR","TR-50","Nevşehir","tr-50-cappadocia","Cappadocia","Göreme",38.6431,34.8289,180,15,"landscape|unesco"),
    ("TR","TR-20","Denizli","tr-20-pamukkale","Pamukkale","Pamukkale",37.9203,29.1213,120,4,"natural|unesco"),
    ("HR","HR-19","Dubrovnik-Neretva","hr-19-dubrovnik","Dubrovnik Old Town","Dubrovnik",42.6407,18.1077,120,2,"walled city|unesco"),
    ("HR","HR-13","Lika-Senj","hr-13-plitvice","Plitvice Lakes","Plitvička Jezera",44.8654,15.5820,180,10,"lakes|unesco"),
    ("HU","HU-BU","Budapest","hu-bu-parliament","Hungarian Parliament","Budapest",47.5073,19.0458,90,2,"landmark"),
    ("PL","PL-MA","Lesser Poland","pl-ma-auschwitz","Auschwitz-Birkenau Memorial","Oświęcim",50.0270,19.2030,180,4,"memorial|unesco"),
    ("PL","PL-MA","Lesser Poland","pl-ma-wieliczka","Wieliczka Salt Mine","Wieliczka",49.9831,20.0546,150,3,"mine|unesco"),
    # ---- Russia
    ("RU","RU-MOW","Moscow","ru-mow-redsquare","Red Square","Moscow",55.7539,37.6208,90,2,"square|unesco"),
    ("RU","RU-SPE","St Petersburg","ru-spe-hermitage","State Hermitage Museum","St Petersburg",59.9398,30.3146,150,2,"museum|art"),
    # ---- Middle East / North Africa
    ("EG","EG-GZ","Giza","eg-gz-pyramids","Pyramids of Giza","Giza",29.9792,31.1342,150,5,"ruins|unesco"),
    ("EG","EG-LX","Luxor","eg-lx-karnak","Karnak Temple","Luxor",25.7188,32.6573,120,3,"temple|unesco"),
    ("EG","EG-ASN","Aswan","eg-asn-abusimbel","Abu Simbel","Abu Simbel",22.3372,31.6258,120,3,"temple|unesco"),
    ("JO","JO-MA","Ma'an","jo-ma-petra","Petra","Wadi Musa",30.3285,35.4444,240,8,"ruins|unesco"),
    ("JO","JO-AQ","Aqaba","jo-aq-wadirum","Wadi Rum","Wadi Rum",29.5765,35.4200,180,20,"desert|unesco"),
    ("AE","AE-DU","Dubai","ae-du-burjkhalifa","Burj Khalifa","Dubai",25.1972,55.2744,90,2,"tower|icon"),
    ("AE","AE-AZ","Abu Dhabi","ae-az-sheikhzayed","Sheikh Zayed Grand Mosque","Abu Dhabi",24.4128,54.4750,90,2,"mosque"),
    ("MA","MA-07","Marrakesh-Safi","ma-07-jemaa","Jemaa el-Fnaa","Marrakesh",31.6258,-7.9891,90,2,"square|unesco"),
    ("MA","MA-01","Tanger-Tetouan","ma-01-chefchaouen","Chefchaouen","Chefchaouen",35.1688,-5.2636,120,3,"town"),
    # ---- Sub-Saharan Africa
    ("ZA","ZA-WC","Western Cape","za-wc-tablemountain","Table Mountain","Cape Town",-33.9628,18.4098,150,6,"mountain|natural"),
    ("ZA","ZA-MP","Mpumalanga","za-mp-kruger","Kruger National Park","Skukuza",-24.9948,31.5900,240,40,"safari|wildlife"),
    ("TZ","TZ-18","Kilimanjaro","tz-18-kilimanjaro","Mount Kilimanjaro","Moshi",-3.0674,37.3556,240,20,"mountain|unesco"),
    ("TZ","TZ-02","Arusha","tz-02-serengeti","Serengeti National Park","Seronera",-2.3333,34.8333,240,50,"safari|unesco"),
    ("KE","KE-30","Narok","ke-30-masaimara","Maasai Mara","Sekenani",-1.4061,35.0080,240,40,"safari|wildlife"),
    # ---- South Asia
    ("IN","IN-UP","Uttar Pradesh","in-up-tajmahal","Taj Mahal","Agra",27.1751,78.0421,120,3,"mausoleum|unesco"),
    ("IN","IN-UP","Uttar Pradesh","in-up-varanasi","Varanasi Ghats","Varanasi",25.3109,83.0104,120,3,"river|sacred"),
    ("IN","IN-RJ","Rajasthan","in-rj-amber","Amber Fort","Jaipur",26.9855,75.8513,120,3,"fort|unesco"),
    ("IN","IN-RJ","Rajasthan","in-rj-jaisalmer","Jaisalmer Fort","Jaisalmer",26.9124,70.9124,120,3,"fort|unesco"),
    ("IN","IN-KL","Kerala","in-kl-backwaters","Kerala Backwaters","Alappuzha",9.4981,76.3388,180,15,"waterways"),
    ("IN","IN-MH","Maharashtra","in-mh-ajanta","Ajanta Caves","Aurangabad",20.5522,75.7033,150,4,"caves|unesco"),
    ("IN","IN-GA","Goa","in-ga-oldgoa","Churches of Old Goa","Old Goa",15.5007,73.9114,90,3,"church|unesco"),
    ("NP","NP-P1","Koshi","np-p1-everestbc","Everest Base Camp","Khumjung",28.0026,86.8528,240,10,"trek|mountain"),
    ("NP","NP-P3","Bagmati","np-p3-durbar","Kathmandu Durbar Square","Kathmandu",27.7045,85.3070,90,2,"square|unesco"),
    # ---- East and Southeast Asia
    ("JP","JP-13","Tokyo","jp-13-sensoji","Sensō-ji","Tokyo",35.7148,139.7967,60,2,"temple"),
    ("JP","JP-13","Tokyo","jp-13-shibuya","Shibuya Crossing","Tokyo",35.6595,139.7005,30,1,"urban|icon"),
    ("JP","JP-26","Kyoto","jp-26-fushimi","Fushimi Inari Taisha","Kyoto",34.9671,135.7727,120,2,"shrine|icon"),
    ("JP","JP-26","Kyoto","jp-26-kinkakuji","Kinkaku-ji","Kyoto",35.0394,135.7292,60,2,"temple|unesco"),
    ("JP","JP-34","Hiroshima","jp-34-itsukushima","Itsukushima Shrine","Hatsukaichi",34.2959,132.3197,90,2,"shrine|unesco"),
    ("JP","JP-34","Hiroshima","jp-34-peacepark","Hiroshima Peace Memorial","Hiroshima",34.3955,132.4536,90,2,"memorial|unesco"),
    ("JP","JP-22","Shizuoka","jp-22-fuji","Mount Fuji","Fujinomiya",35.3606,138.7274,180,15,"mountain|unesco"),
    ("CN","CN-BJ","Beijing","cn-bj-greatwall","Great Wall at Mutianyu","Beijing",40.4319,116.5704,180,6,"wall|unesco"),
    ("CN","CN-BJ","Beijing","cn-bj-forbidden","Forbidden City","Beijing",39.9163,116.3972,150,2,"palace|unesco"),
    ("CN","CN-SN","Shaanxi","cn-sn-terracotta","Terracotta Army","Xi'an",34.3841,109.2785,120,3,"ruins|unesco"),
    ("CN","CN-HN","Hunan","cn-hn-zhangjiajie","Zhangjiajie","Zhangjiajie",29.3150,110.4344,240,20,"landscape|unesco"),
    ("KR","KR-11","Seoul","kr-11-gyeongbokgung","Gyeongbokgung Palace","Seoul",37.5796,126.9770,120,2,"palace"),
    ("KR","KR-49","Jeju","kr-49-seongsan","Seongsan Ilchulbong","Jeju",33.4586,126.9425,90,3,"volcanic|unesco"),
    ("TH","TH-10","Bangkok","th-10-grandpalace","Grand Palace","Bangkok",13.7500,100.4913,120,2,"palace"),
    ("TH","TH-10","Bangkok","th-10-watarun","Wat Arun","Bangkok",13.7437,100.4889,60,2,"temple"),
    ("TH","TH-64","Sukhothai","th-64-sukhothai","Sukhothai Historical Park","Sukhothai",17.0206,99.7036,150,5,"ruins|unesco"),
    ("TH","TH-83","Phuket","th-83-phiphi","Phi Phi Islands","Phuket",7.7407,98.7784,180,10,"island|coast"),
    ("VN","VN-13","Quảng Ninh","vn-13-halong","Ha Long Bay","Hạ Long",20.9101,107.1839,180,20,"bay|unesco"),
    ("VN","VN-49","Quảng Nam","vn-49-hoian","Hội An Ancient Town","Hội An",15.8801,108.3380,120,2,"town|unesco"),
    ("KH","KH-13","Siem Reap","kh-13-angkorwat","Angkor Wat","Siem Reap",13.4125,103.8670,240,10,"temple|unesco"),
    ("ID","ID-BA","Bali","id-ba-tanahlot","Tanah Lot","Tabanan",-8.6212,115.0868,90,2,"temple|coast"),
    ("ID","ID-JI","East Java","id-ji-bromo","Mount Bromo","Probolinggo",-7.9425,112.9530,150,8,"volcano"),
    ("ID","ID-JT","Central Java","id-jt-borobudur","Borobudur","Magelang",-7.6079,110.2038,150,3,"temple|unesco"),
    # ---- Oceania
    ("AU","AU-NSW","New South Wales","au-nsw-operahouse","Sydney Opera House","Sydney",-33.8568,151.2153,90,2,"landmark|unesco"),
    ("AU","AU-QLD","Queensland","au-qld-greatbarrier","Great Barrier Reef","Cairns",-16.2864,145.7781,240,50,"reef|unesco"),
    ("AU","AU-NT","Northern Territory","au-nt-uluru","Uluru","Yulara",-25.3444,131.0369,150,15,"monolith|unesco"),
    ("AU","AU-VIC","Victoria","au-vic-greatocean","Twelve Apostles","Port Campbell",-38.6662,143.1044,90,5,"coast"),
    ("NZ","NZ-OTA","Otago","nz-ota-milford","Milford Sound","Milford Sound",-44.6714,167.9256,180,15,"fjord|unesco"),
    ("NZ","NZ-WKO","Waikato","nz-wko-hobbiton","Hobbiton Movie Set","Matamata",-37.8721,175.6828,120,3,"film"),
    ("NZ","NZ-CAN","Canterbury","nz-can-aoraki","Aoraki / Mount Cook","Mount Cook",-43.5950,170.1418,180,20,"mountain|unesco"),
    # ---- Americas beyond the US
    ("CA","CA-AB","Alberta","ca-ab-banff","Banff National Park","Banff",51.4968,-115.9281,240,30,"park|unesco"),
    ("CA","CA-BC","British Columbia","ca-bc-stanley","Stanley Park","Vancouver",49.3017,-123.1417,120,5,"park"),
    ("CA","CA-QC","Quebec","ca-qc-oldquebec","Old Québec","Québec City",46.8123,-71.2145,120,3,"district|unesco"),
    ("CA","CA-ON","Ontario","ca-on-niagara","Niagara Falls","Niagara Falls",43.0896,-79.0849,90,3,"falls"),
    ("MX","MX-ROO","Quintana Roo","mx-roo-chichenitza","Chichén Itzá","Tinum",20.6843,-88.5678,150,4,"ruins|unesco"),
    ("MX","MX-ROO","Quintana Roo","mx-roo-tulum","Tulum Ruins","Tulum",20.2149,-87.4291,90,3,"ruins|coast"),
    ("MX","MX-CMX","Mexico City","mx-cmx-teotihuacan","Teotihuacán","San Juan Teotihuacán",19.6925,-98.8438,150,5,"ruins|unesco"),
    ("PE","PE-CUS","Cusco","pe-cus-machupicchu","Machu Picchu","Aguas Calientes",-13.1631,-72.5450,180,5,"ruins|unesco"),
    ("PE","PE-CUS","Cusco","pe-cus-rainbow","Rainbow Mountain","Pitumarca",-13.8690,-71.3030,180,8,"mountain"),
    ("BR","BR-RJ","Rio de Janeiro","br-rj-christ","Christ the Redeemer","Rio de Janeiro",-22.9519,-43.2105,90,3,"statue|icon"),
    ("BR","BR-RJ","Rio de Janeiro","br-rj-sugarloaf","Sugarloaf Mountain","Rio de Janeiro",-22.9492,-43.1545,90,3,"mountain"),
    ("BR","BR-PR","Paraná","br-pr-iguacu","Iguaçu Falls","Foz do Iguaçu",-25.6953,-54.4367,180,10,"falls|unesco"),
    ("AR","AR-B","Buenos Aires","ar-b-recoleta","Recoleta Cemetery","Buenos Aires",-34.5875,-58.3936,90,2,"cemetery"),
    ("AR","AR-Z","Santa Cruz","ar-z-perito","Perito Moreno Glacier","El Calafate",-50.4967,-73.1377,150,10,"glacier|unesco"),
    ("CL","CL-VS","Valparaíso","cl-vs-easter","Rapa Nui (Easter Island)","Hanga Roa",-27.1127,-109.3497,240,15,"statues|unesco"),
    ("CL","CL-AN","Antofagasta","cl-an-atacama","Atacama Desert","San Pedro de Atacama",-22.9087,-68.1997,180,30,"desert"),
]


def validate():
    errs = []
    ids = [r[3] for r in P]
    if len(ids) != len(set(ids)):
        dupes = {i for i in ids if ids.count(i) > 1}
        errs.append(f"duplicate placeIds: {sorted(dupes)}")
    for c, rc, rn, pid, name, city, lat, lng, dwell, geo, tags in P:
        if c not in BBOX:
            errs.append(f"{pid}: no bounding box for {c}")
            continue
        la0, la1, ln0, ln1 = BBOX[c]
        if not (la0 <= lat <= la1 and ln0 <= lng <= ln1):
            errs.append(f"{pid}: ({lat},{lng}) outside {c} bbox — transposed or mistyped?")
        if not rc.startswith(c + "-"):
            errs.append(f"{pid}: region '{rc}' does not belong to {c}")
        if not pid.startswith(c.lower() + "-"):
            errs.append(f"{pid}: id does not encode its country")
        if dwell < 15 or dwell > 480:
            errs.append(f"{pid}: implausible dwell {dwell} min")
        if geo < 1 or geo > 60:
            errs.append(f"{pid}: implausible geofence {geo} km")
        for f in (name, city, rn):
            if "\t" in f or "|" in f:
                errs.append(f"{pid}: delimiter inside a field")
    return errs


if __name__ == "__main__":
    errs = validate()
    if errs:
        for e in errs:
            print("FAIL:", e)
        raise SystemExit(1)

    countries = sorted({r[0] for r in P})
    regions = sorted({r[1] for r in P})
    rows = ["\t".join([
        "place_id", "country", "region_code", "region_name", "name", "city",
        "lat", "lng", "dwell_seconds", "geofence_meters", "tags",
    ])]
    for c, rc, rn, pid, name, city, lat, lng, dwell, geo, tags in sorted(P, key=lambda r: r[3]):
        rows.append("\t".join([
            pid, c, rc, rn, name, city, f"{lat}", f"{lng}",
            str(dwell * 60), str(geo * 1000), tags,
        ]))
    out = "/sessions/great-nice-gates/mnt/outputs/canon-world.tsv"
    open(out, "w").write("\n".join(rows) + "\n")

    print(f"OK  {len(P)} places · {len(countries)} countries · {len(regions)} regions")
    per = {}
    for r in P:
        per[r[0]] = per.get(r[0], 0) + 1
    top = sorted(per.items(), key=lambda kv: -kv[1])[:8]
    print("     most covered:", ", ".join(f"{k}={v}" for k, v in top))
    print(f"     -> {out}")
