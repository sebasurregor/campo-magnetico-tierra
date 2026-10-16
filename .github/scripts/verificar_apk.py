#!/usr/bin/env python3
"""Verifica el APK final con aapt2 y termina con error si algo no cumple la política de privacidad.

Comprueba que:
  * el único permiso (usado o declarado) sea android.permission.ACCESS_COARSE_LOCATION;
  * solo la Activity principal esté exportada;
  * no haya servicios;
  * android:allowBackup sea false.

Uso: verificar_apk.py <ruta al APK>
"""
import glob
import os
import re
import subprocess
import sys

PERMITIDOS = {"android.permission.ACCESS_COARSE_LOCATION"}
ACTIVITY_PRINCIPAL = "com.fisica2.campomagnetico.MainActivity"
COMPONENTES = {"activity", "activity-alias", "service", "receiver", "provider"}


def buscar_aapt2():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sys.exit("ERROR: no está definida ANDROID_HOME")
    rutas = glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2"))
    if not rutas:
        sys.exit("ERROR: no se encontró aapt2 en " + sdk)

    def version(ruta):
        nombre = os.path.basename(os.path.dirname(ruta))
        return [int(p) if p.isdigit() else -1 for p in re.split(r"[.\-]", nombre)]

    return sorted(rutas, key=version)[-1]


def ejecutar(cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def verificar_permisos(aapt2, apk, errores):
    salida = ejecutar([aapt2, "dump", "permissions", apk])
    print("=== Permisos del APK (aapt2 dump permissions) ===")
    print(salida.strip())
    print("=================================================")
    encontrados = []
    for linea in salida.splitlines():
        linea = linea.strip()
        m = re.match(r"^(uses-permission[\w-]*|permission):\s*(?:name=')?([^'\s]+)", linea)
        if m:
            encontrados.append((m.group(1), m.group(2)))
    for tipo, nombre in encontrados:
        if nombre not in PERMITIDOS:
            errores.append(f"Permiso no permitido: {tipo} {nombre}")
    if not any(n in PERMITIDOS for _, n in encontrados):
        errores.append("No se encontró ACCESS_COARSE_LOCATION: no se pudo verificar la lista de permisos")
    return [n for _, n in encontrados]


def leer_manifiesto(aapt2, apk):
    """Convierte la salida de 'aapt2 dump xmltree' en un árbol de diccionarios."""
    salida = ejecutar([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk])
    raiz = {"tag": "#doc", "attrs": {}, "hijos": []}
    pila = [(-1, raiz)]
    for linea in salida.splitlines():
        sangria = len(linea) - len(linea.lstrip())
        texto = linea.strip()
        m = re.match(r"^E: (\S+)", texto)
        if m:
            while pila and pila[-1][0] >= sangria:
                pila.pop()
            nodo = {"tag": m.group(1), "attrs": {}, "hijos": []}
            pila[-1][1]["hijos"].append(nodo)
            pila.append((sangria, nodo))
            continue
        m = re.match(r"^A: (?:\S*:)?([A-Za-z_]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)$", texto)
        if m:
            while pila and pila[-1][0] >= sangria:
                pila.pop()
            valor = m.group(2).strip()
            m2 = re.match(r'^"([^"]*)"', valor)
            if m2:
                valor = m2.group(1)
            pila[-1][1]["attrs"][m.group(1)] = valor
    return raiz


def es_verdadero(valor):
    v = valor.lower()
    return "true" in v or v.endswith("0xffffffff")


def buscar(nodo, tag):
    if nodo["tag"] == tag:
        yield nodo
    for h in nodo["hijos"]:
        yield from buscar(h, tag)


def verificar_manifiesto(aapt2, apk, errores):
    raiz = leer_manifiesto(aapt2, apk)
    manifiesto = next(buscar(raiz, "manifest"), None)
    aplicacion = next(buscar(raiz, "application"), None)
    if manifiesto is None or aplicacion is None:
        errores.append("No se pudo leer el AndroidManifest.xml del APK")
        return
    paquete = manifiesto["attrs"].get("package", "")

    backup = aplicacion["attrs"].get("allowBackup")
    print(f"android:allowBackup = {backup}")
    if backup is None or es_verdadero(backup):
        errores.append("android:allowBackup no es false")

    print("=== Componentes ===")
    for nodo in aplicacion["hijos"]:
        if nodo["tag"] not in COMPONENTES:
            continue
        nombre = nodo["attrs"].get("name", "?")
        if nombre.startswith("."):
            nombre = paquete + nombre
        exp = nodo["attrs"].get("exported")
        tiene_filtro = any(h["tag"] == "intent-filter" for h in nodo["hijos"])
        if exp is None:
            exportado = tiene_filtro and nodo["tag"] != "provider"
        else:
            exportado = es_verdadero(exp)
        print(f"  {nodo['tag']:15} exported={str(exportado).lower():5} {nombre}")
        if nodo["tag"] == "service":
            errores.append(f"El APK contiene un servicio: {nombre}")
        if exportado and not (nodo["tag"] == "activity" and nombre == ACTIVITY_PRINCIPAL):
            errores.append(f"Componente exportado no permitido: {nodo['tag']} {nombre}")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    apk = sys.argv[1]
    aapt2 = buscar_aapt2()
    print(f"Usando {aapt2}")
    errores = []
    permisos = verificar_permisos(aapt2, apk, errores)
    verificar_manifiesto(aapt2, apk, errores)

    resumen = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumen:
        with open(resumen, "a", encoding="utf-8") as f:
            f.write("### Permisos del APK\n\n")
            for p in permisos:
                f.write(f"- `{p}`\n")
            f.write("\n" + ("❌ Verificación fallida\n" if errores else "✅ Verificación correcta\n"))

    if errores:
        for e in errores:
            print(f"::error::{e}")
        sys.exit(1)
    print("OK: el APK solo usa ACCESS_COARSE_LOCATION, solo la Activity principal está exportada y allowBackup=false")


if __name__ == "__main__":
    main()
