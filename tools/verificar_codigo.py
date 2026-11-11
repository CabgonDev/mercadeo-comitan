#!/usr/bin/env python3
"""Comprobaciones estructurales offline. NO sustituye compilar, lint o pruebas Firebase."""
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as etree

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app' / 'src' / 'main'
ANDROID = '{http://schemas.android.com/apk/res/android}'
problems = []
checks = []

def verify(condition: bool, msg: str) -> None:
    checks.append((bool(condition), msg))
    if not condition:
        problems.append(msg)

xml_files = sorted(APP.rglob('*.xml'))
parsed = {}
for file in xml_files:
    try:
        parsed[file] = etree.parse(str(file))
    except (etree.ParseError, OSError) as err:
        problems.append(f'XML invalido {file.relative_to(ROOT)}: {err}')
verify(len(parsed) == len(xml_files), f'{len(parsed)}/{len(xml_files)} XML validos')

manifest_file = APP / 'AndroidManifest.xml'
manifest = parsed.get(manifest_file)
verify(manifest is not None, 'AndroidManifest.xml presente')
if manifest is not None:
    app = manifest.getroot().find('application')
    verify(app is not None, 'Elemento application presente')
    if app is not None:
        verify(app.get(ANDROID+'allowBackup') == 'false', 'Copia de seguridad Android deshabilitada')
        verify(app.get(ANDROID+'usesCleartextTraffic') == 'false', 'Trafico HTTP no cifrado deshabilitado')
        services = [s.get(ANDROID+'name','') for s in app.findall('service')]
        verify('.Chat.MyFirebaseMessagingService' in services, 'Servicio FCM declarado')

kotlin_files = sorted(APP.rglob('*.kt'))
svc_match = False
for file in kotlin_files:
    data = file.read_text(encoding='utf8')
    if re.search(r'^package\s+com\.cabgon\.mercadeocomitan\.Chat\s*$', data, re.M) and re.search(r'\bclass\s+MyFirebaseMessagingService\b', data):
        svc_match = True
verify(svc_match, 'Clase real del servicio FCM coincide con manifiesto')

unlabeled = []
for file in (APP / 'res' / 'layout').glob('*.xml'):
    tree = parsed.get(file)
    if tree is None: continue
    for node in tree.iter():
        if isinstance(node.tag, str) and node.tag.rsplit('}', 1)[-1] == 'ImageButton' and not node.get(ANDROID+'contentDescription'):
            unlabeled.append((str(file.relative_to(ROOT)), node.get(ANDROID+'id')))
verify(not unlabeled, f'ImageButton sin contentDescription: {len(unlabeled)}')

strings_file = APP / 'res' / 'values' / 'strings.xml'
strings = parsed.get(strings_file)
strings_set = set()
if strings is not None:
    strings_set = {n.get('name') for n in strings.getroot().findall('string')}
for name in ['accion_volver','accion_cerrar','accion_favorito','home_empty_catalog','home_empty_search']:
    verify(name in strings_set, f'Recurso string presente: {name}')

new_layout = parsed.get(APP / 'res' / 'layout' / 'fragment_inicio.xml')
if new_layout is not None:
    verify(any(node.get(ANDROID+'id') == '@+id/tvEstadoCatalogo' for node in new_layout.iter()), 'Vista de estado vacio declarada')

refs_to_check = []
for xml_file,tree in parsed.items():
    for n in tree.iter():
        for value in n.attrib.values():
            for match in re.finditer(r'@string/(home_empty_[A-Za-z0-9_]+|accion_[A-Za-z0-9_]+)', value):
                refs_to_check.append((xml_file, match.group(1)))
verify(all(name in strings_set for _,name in refs_to_check), 'Referencias a nuevas cadenas resueltas')

billing = (APP / 'java' / 'com' / 'cabgon' / 'mercadeocomitan' / 'billing' / 'BillingHelper.kt').read_text(encoding='utf8')
verify('if (tarea.isSuccessful)' in billing, 'Resultado asincrono de pago gestionado')
base = APP / 'java' / 'com' / 'cabgon' / 'mercadeocomitan'
main = (base / 'MainActivity.kt').read_text(encoding='utf8')
verify('Token actual: $token' not in main and 'Token tras refresh: $t2' not in main, 'Tokens FCM no registrados en logcat')
chat = (base / 'Chat' / 'ChatActivity.kt').read_text(encoding='utf8')
verify('if (!chatConfigurado) return' in chat and 'mensajesQuery?.removeEventListener(it)' in chat, 'Ciclo de vida y listener Chat protegidos')
verify('setValue(ServerValue.increment(1L))' in (base / 'Constantes.kt').read_text(encoding='utf8'), 'Contador de vistas atomico')

# Una ausencia NO es un error del script: es un riesgo pendiente que requiere backend.
functions_dir = ROOT / 'functions'
rules = [ROOT / 'database.rules.json', ROOT / 'storage.rules']
print(f'Archivos fuente Kotlin: {len(kotlin_files)}, XML: {len(xml_files)}')
print(f'Functions versionadas: {functions_dir.is_dir()}, reglas versionadas: {sum(p.is_file() for p in rules)}/{len(rules)}')
for okay,msg in checks:
    print(('OK  ' if okay else 'FAIL')+' '+msg)
for problem in problems:
    if problem not in [msg for ok,msg in checks if not ok]:
        print('FAIL '+problem)
print(f'Resultado: {len(checks)-len([v for v,_ in checks if not v])}/{len(checks)} comprobaciones OK')
sys.exit(1 if problems else 0)
