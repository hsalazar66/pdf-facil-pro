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
def stage_fixture():
    # Inject only into the debuggable test APK; production APKs do not permit run-as.
    adb('shell', 'run-as', APP, 'mkdir', '-p', 'cache/visor_demo')
    subprocess.run(['adb', 'shell', '-T', 'run-as', APP, 'dd',
                    'of=cache/visor_demo/imagen-movible.pdf', 'bs=4096'],
                   input=fixture.read_bytes(), stdout=subprocess.PIPE,
                   stderr=subprocess.PIPE, check=True, timeout=30)
    actual = adb('exec-out', 'run-as', APP, 'cat', 'cache/visor_demo/imagen-movible.pdf', binary=True)
    assert actual == fixture.read_bytes(), 'Se alteró el PDF de prueba al copiarlo'
    return 'content://' + APP + '.files/visor_demo/imagen-movible.pdf'

fixture_uri = stage_fixture()
adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity', '-a', 'android.intent.action.VIEW',
    '-t', 'application/pdf', '-d', fixture_uri)
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

# The sharing menu must expose each destination without sending anything.
tap('Compartir')
wait_for('Compartir y guardar')
for destination in ('WhatsApp', 'Google Drive', 'Correo electrónico', 'Guardar en el celular'):
    wait_for(destination)
screenshot('07-compartir-y-guardar')
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)

# Real bitmap editing and clipboard actions.
tap('Redimensionar')
l,t,r,b=bounds(wait_for('Esquina inferior derecha de imagen'))
hx=(l+r)//2;hy=(t+b)//2
adb('shell','input','swipe',str(hx),str(hy),str(hx+24),str(hy+24),'900')
wait_for('Imagen redimensionada',partial=True)
screenshot('08-imagen-redimensionada-interactiva')
drafts=adb('shell','run-as',APP,'ls','files/documentos').splitlines()
name=next(n for n in drafts if n.endswith('-imagen-movible.pdf'))
(OUT/'redimensionado.pdf').write_bytes(adb('exec-out','run-as',APP,'cat','files/documentos/'+name,binary=True))
tap('Terminar')
tap('Editar imagen')
tap('Girar 90°')
wait_for('Imagen girada', partial=True)
tap('Editar imagen')
tap('Recortar')
preview = wait_for('Previsualización de imagen')
l, t, r, b = bounds(preview)
adb('shell', 'input', 'swipe', str(round(l+(r-l)*0.05)), str(round(t+(b-t)*0.05)),
    str(round(l+(r-l)*0.45)), str(round(t+(b-t)*0.95)), '700')
tap('Aplicar recorte')
wait_for('Imagen recortada', partial=True)
screenshot('09-imagen-recortada')
tap('Copiar')
wait_for('Imagen copiada', partial=True)
tap('Cortar')
wait_for('Imagen cortada', partial=True)
tap('Pegar')
l, t, r, b = bounds(wait_for('Página 1 del documento'))
adb('shell', 'input', 'tap', str(round(l+(r-l)*0.1)), str(round(t+(b-t)*0.2)))
wait_for('Imagen pegada', partial=True)
screenshot('10-imagen-pegada')

# Add a blank page, write real text, cut it, and paste at a new position.
tap('Herramientas')
tap('Hoja nueva')
wait_for('Añadir hoja nueva')
tap('Añadir hoja')
wait_for('Página 2 de 2', partial=True)
l, t, r, b = bounds(wait_for('Página 2 del documento'))
adb('shell', 'input', 'tap', str(round(l+(r-l)*0.12)), str(round(t+(b-t)*0.18)))
wait_for('Añadir texto nuevo')
tap('Texto nuevo')
adb('shell', 'input', 'text', 'TextoClipboard')
tap('Añadir')
wait_for('Texto nuevo añadido', partial=True)
tap('Modificar')
def select_test_text():
    l, t, r, b = bounds(wait_for('Página 2 del documento'))
    adb('shell', 'input', 'tap', str(round(l+(r-l)*0.2)), str(round(t+(b-t)*0.173)))
    wait_for('Editar texto original')
select_test_text()
tap('Copiar')
wait_for('Texto copiado', partial=True)
select_test_text()
tap('Cortar')
wait_for('Texto cortado', partial=True)
tap('Pegar')
l, t, r, b = bounds(wait_for('Página 2 del documento'))
adb('shell', 'input', 'tap', str(round(l+(r-l)*0.15)), str(round(t+(b-t)*0.30)))
wait_for('Texto pegado', partial=True)
screenshot('11-hoja-nueva-texto-pegado')

# Exercise Android's real CreateDocument flow and retrieve the exported PDF.
tap('Compartir')
tap('Guardar en el celular')
deadline=time.monotonic()+55
saved=False
while time.monotonic()<deadline:
    current=tree()
    node=next((n for n in current.iter('node') if n.attrib.get('resource-id','').endswith(':id/action_menu_save')),None)
    if node is None:
        node=next((find(current,name) for name in ('GUARDAR','Guardar','SAVE','Save') if find(current,name) is not None),None)
    if node is not None:
        l,t,r,b=bounds(node)
        adb('shell','input','tap',str((l+r)//2),str((t+b)//2));saved=True;break
    time.sleep(1)
assert saved, 'No se encontró Guardar en el selector de archivos'
wait_for('Cambios guardados')
exported=adb('exec-out','cat','/sdcard/Download/imagen-movible-editado.pdf',binary=True)
assert exported.startswith(b'%PDF-'), 'El archivo exportado no es PDF'
(OUT/'resultado-editado.pdf').write_bytes(exported)
screenshot('12-pdf-guardado')
# Insert an actual vector/text table and select several rows for external clipboard.
tap('Herramientas');tap('Tabla');wait_for('Insertar tabla')
for label,value in [('1,1','Nombre'),('1,2','Monto'),('2,1','Jose'),('2,2','42')]:
    tap(label);adb('shell','input','text',value)
    adb('shell','input','keyevent','4');time.sleep(0.4)
tap('Colocar tabla')
l,t,r,b=bounds(wait_for('Página 2 del documento'))
adb('shell','input','tap',str(round(l+(r-l)*0.08)),str(round(t+(b-t)*0.55)))
wait_for('Tabla insertada',partial=True)
tap('Seleccionar varias líneas / tabla')
l,t,r,b=bounds(wait_for('Seleccionar varias líneas y tablas'))
adb('shell','input','swipe',str(round(l+(r-l)*0.05)),str(round(t+(b-t)*0.53)),str(round(l+(r-l)*0.92)),str(round(t+(b-t)*0.68)),'900')
tap('Copiar líneas');wait_for('Varias líneas copiadas',partial=True)
tap('Copiar tabla');wait_for('Revisar tabla para copiar');screenshot('13-tabla-seleccionada');tap('Copiar tabla');wait_for('Tabla copiada',partial=True)
tap('Cerrar selección')
# Export real DOCX and reconstructed PDF through Android's document picker.
def save_picker():
    deadline=time.monotonic()+70
    while time.monotonic()<deadline:
        current=tree()
        n=next((n for n in current.iter('node') if n.attrib.get('resource-id','').endswith(':id/action_menu_save')),None)
        if n is None:
            n=next((n for n in current.iter('node') if 'documentsui' in n.attrib.get('package','') and n.attrib.get('text') in ('SAVE','Save','GUARDAR','Guardar')),None)
        if n is not None:
            l,t,r,b=bounds(n);adb('shell','input','tap',str((l+r)//2),str((t+b)//2));return
        time.sleep(1)
    raise AssertionError('No apareció el selector de exportación')
for option,suffix,result in [('Word editable (.docx)','docx','Word editable guardado'),('PDF editable (texto)','pdf','PDF editable de texto guardado')]:
    tap('Herramientas');tap('Exportar');tap(option);save_picker();wait_for(result,partial=True)
    data=adb('exec-out','cat','/sdcard/Download/imagen-movible-editable.'+suffix,binary=True)
    (OUT/('exportado-editable.'+suffix)).write_bytes(data)
    assert data.startswith(b'PK' if suffix=='docx' else b'%PDF-')
screenshot('14-exportacion-editable')

# OCR a genuinely scanned PDF; model must work in this fresh emulator without downloads.
scan=Path('app/build/verification/escaneo-ocr.pdf');assert scan.is_file()
adb('shell','run-as',APP,'mkdir','-p','cache/visor_demo')
subprocess.run(['adb','shell','-T','run-as',APP,'dd','of=cache/visor_demo/escaneo-ocr.pdf','bs=4096'],input=scan.read_bytes(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
adb('shell','am','start','-W','-n',APP+'/.MainActivity','-a','android.intent.action.VIEW','-t','application/pdf','-d','content://'+APP+'.files/visor_demo/escaneo-ocr.pdf')
wait_for('Confirmar');tap('Continuar');wait_for('escaneo-ocr.pdf')
for option,suffix,result in [('Word editable (.docx)','docx','Word editable guardado'),('PDF editable (texto)','pdf','PDF editable de texto guardado')]:
    tap('Herramientas');tap('Exportar');tap(option);save_picker();wait_for(result,partial=True)
    data=adb('exec-out','cat','/sdcard/Download/escaneo-ocr-editable.'+suffix,binary=True)
    (OUT/('ocr-editable.'+suffix)).write_bytes(data)
screenshot('15-ocr-local-exportado')

# A clean first launch from an external PDF must also display the offer.
adb('shell', 'pm', 'clear', APP)
fixture_uri = stage_fixture()
adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity', '-a', 'android.intent.action.VIEW',
    '-t', 'application/pdf', '-d', fixture_uri)
wait_for(QUESTION)
screenshot('06-oferta-desde-pdf-externo')
tap('Ahora no')
wait_for('imagen-movible.pdf')
(OUT / 'RESULTADO.txt').write_text(
    'OK: oferta al instalar; Ahora no; repetición manual; actualización sin desinstalar '
    'y con borrador conservado; oferta después de actualizar; apertura externa de PDF; '
    'selección, arrastre, tamaño interactivo con los dedos, giro, recorte y portapapeles de imagen; hoja nueva, copiar/cortar/pegar texto; menú de compartir y guardado de PDF real; insertar tabla, copiar varias líneas y tabla; exportar DOCX y PDF de texto. Android API 29 emulado. '
    'La elección Siempre y el comportamiento de cada fabricante requieren prueba en teléfono.\n'
)
print('VERIFICACION ANDROID COMPLETADA')
