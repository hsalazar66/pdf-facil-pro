"""Real Android smoke checks on the GitHub emulator; only synthetic documents."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP = 'com.pdffacil.pro'
APK = Path('app/build/outputs/apk/debug/app-debug.apk')
OUT = Path('verification-android')
OUT.mkdir(exist_ok=True)
QUESTION = '¿Quieres abrir tus PDF con PDF Fácil Pro?'


def adb(*args, binary=False):
    return subprocess.check_output(['adb', *args], text=not binary, timeout=40)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/pdf-facil-ui.xml')
    xml = adb('exec-out', 'cat', '/sdcard/pdf-facil-ui.xml')
    (OUT / 'last-ui.xml').write_text(xml)
    return ET.fromstring(xml)


def find(root, label, partial=False):
    for node in root.iter('node'):
        for field in ('text', 'content-desc'):
            value = node.attrib.get(field, '')
            if value == label or (partial and label in value):
                return node
    return None


def wait_for(label, partial=False, seconds=55):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = find(tree(), label, partial)
            if result is not None:
                return result
        except (ET.ParseError, subprocess.CalledProcessError):
            pass
        time.sleep(1)
    screenshot('error')
    raise AssertionError('No apareció: ' + label)


def bounds(node):
    values = list(map(int, re.findall(r'\d+', node.attrib['bounds'])))
    assert len(values) == 4, node.attrib
    return values


def tap(label):
    left, top, right, bottom = bounds(wait_for(label))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))
    time.sleep(1)


def screenshot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))


def launch():
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity')


def assert_no_offer():
    wait_for('¿Qué quieres hacer hoy?')
    for _ in range(3):
        assert find(tree(), QUESTION) is None, 'La oferta se repitió sin actualizar'
        time.sleep(1)


adb('wait-for-device')
adb('install', '-r', str(APK))
launch()
wait_for(QUESTION)
screenshot('01-oferta-instalacion')
tap('Ahora no')
launch()
assert_no_offer()
tap('Abrir PDF siempre aquí')
wait_for(QUESTION)
tap('Ahora no')
tap('Probar edición con un PDF de ejemplo')
wait_for('Informe de trabajo.pdf')
tap('Anterior')  # Header back button precedes the page-navigation button.
wait_for('Informe de trabajo.pdf')
screenshot('02-borrador-antes-de-actualizar')
time.sleep(2)
adb('install', '-r', str(APK))  # Same signing key, same app: update without uninstalling.
launch()
wait_for(QUESTION)
screenshot('03-oferta-despues-de-actualizar')
tap('Ahora no')
wait_for('Informe de trabajo.pdf')  # Private document survived the package update.

fixture = Path('app/build/verification/imagen-movible.pdf')
assert fixture.is_file(), 'Falta el PDF generado por la prueba de imagen'
adb('push', str(fixture), '/sdcard/Download/imagen-movible.pdf')
inserted = adb('shell', 'content', 'insert', '--uri', 'content://media/external/file',
               '--bind', '_data:s:/sdcard/Download/imagen-movible.pdf',
               '--bind', 'mime_type:s:application/pdf',
               '--bind', '_display_name:s:imagen-movible.pdf')
match = re.search(r'content://[^\s]+', inserted)
if match:
    fixture_uri = match.group(0)
else:
    # Android 10's content command can insert successfully without printing the URI.
    rows = adb('shell', 'content', 'query', '--uri', 'content://media/external/file', '--projection', '_id:_display_name')
    row = next((line for line in rows.splitlines() if 'imagen-movible.pdf' in line), '')
    ident = re.search(r'_id=(\d+)', row)
    assert ident, 'No se encontró la fila de prueba: ' + rows
    fixture_uri = 'content://media/external/file/' + ident.group(1)
adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity', '-a', 'android.intent.action.VIEW',
    '-t', 'application/pdf', '-d', fixture_uri, '--grant-read-uri-permission')
wait_for('imagen-movible.pdf')
tap('Editar')
tap('Imagen')
page = wait_for('Página 1 del documento')
x0, y0, x1, y1 = bounds(page)
x = round(x0 + (100/612)*(x1-x0))
y = round(y0 + (1-190/792)*(y1-y0))
adb('shell', 'input', 'tap', str(x), str(y))
wait_for('Imagen 1 seleccionada')
screenshot('04-imagen-seleccionada')
adb('shell', 'input', 'swipe', str(x), str(y), str(x+60), str(y-45), '700')
wait_for('Imagen movida', partial=True)
screenshot('05-imagen-movida')
# A clean first launch from an external PDF must also display the offer.
adb('shell', 'pm', 'clear', APP)
adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity', '-a', 'android.intent.action.VIEW',
    '-t', 'application/pdf', '-d', fixture_uri, '--grant-read-uri-permission')
wait_for(QUESTION)
screenshot('06-oferta-desde-pdf-externo')
tap('Ahora no')
wait_for('imagen-movible.pdf')
(OUT / 'RESULTADO.txt').write_text(
    'OK: oferta al instalar; Ahora no; repetición manual; actualización sin desinstalar '
    'y con borrador conservado; oferta después de actualizar; apertura externa de PDF; '
    'selección y arrastre de una imagen existente. Android API 29 emulado. '
    'La elección Siempre y el comportamiento de cada fabricante requieren prueba en teléfono.\n'
)
print('VERIFICACION ANDROID COMPLETADA')
