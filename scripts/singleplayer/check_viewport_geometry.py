#!/usr/bin/env python3
"""Exercise the APK's Cacio screen, including its actual composited pixels."""

import argparse
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bootstrap', type=Path, required=True)
    parser.add_argument('--rt4', type=Path, required=True)
    parser.add_argument('--cacio-dir', type=Path, required=True)
    parser.add_argument('--classes', type=Path, default=Path('build/viewport-geometry-tests'))
    args = parser.parse_args()
    jars = sorted(args.cacio_dir.resolve().glob('*.jar'))
    if len(jars) < 2:
        raise SystemExit('Expected the APK Cacio 17 JARs')
    args.classes.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(str(p.resolve()) for p in
                               [args.classes, args.bootstrap, args.rt4, *jars])
    source = Path(__file__).resolve().parents[2] / (
        'singleplayer/native-refactor/tests/ViewportGeometryRegression.java')
    subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17',
                    '-cp', classpath, '-d', str(args.classes), str(source)], check=True)

    java_args = [
        '-Djava.awt.headless=false',
        # Desktop font-cache creation formats a timestamp while Cacio's custom
        # system loader is still being constructed. COMPAT avoids CLDR service
        # discovery re-entering that loader. This is only the CI probe JVM.
        '-Djava.locale.providers=COMPAT',
        '-Dcacio.managed.screensize=1289x503',
        '-Dcacio.font.fontmanager=sun.awt.X11FontManager',
        '-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler',
        '-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel',
        '-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit',
        '-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment',
        '-Djava.system.class.loader=com.github.caciocavallosilano.cacio.ctc.CTCPreloadClassLoader',
        '-Xbootclasspath/a:' + os.pathsep.join(map(str, jars)),
    ]
    for package in ('java.awt', 'java.awt.peer', 'sun.awt.image', 'sun.java2d',
                    'java.awt.dnd.peer', 'sun.awt', 'sun.awt.event',
                    'sun.awt.datatransfer', 'sun.font'):
        java_args.append('--add-exports=java.desktop/' + package + '=ALL-UNNAMED')
    java_args.append('--add-exports=java.base/sun.security.action=ALL-UNNAMED')
    for module, package in (('java.base', 'java.util'), ('java.desktop', 'java.awt'),
                            ('java.desktop', 'sun.font'), ('java.desktop', 'sun.java2d'),
                            ('java.base', 'java.lang.reflect')):
        java_args.append('--add-opens=' + module + '/' + package + '=ALL-UNNAMED')
    environment = os.environ.copy()
    environment.setdefault('LD_LIBRARY_PATH', '')
    subprocess.run(['java', *java_args, '-cp', classpath, 'rt4.ViewportGeometryRegression'],
                   env=environment, check=True, timeout=45)


if __name__ == '__main__':
    main()
