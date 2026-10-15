# Preparation
- [ ] Geeigneten Startpunkt finden

## Arena
- [ ] Füllhorn-Generierung
- [ ] Lobby-Generierung
- [ ] Underground Lobby-Generierung
- [ ] Startplattform-Generierung
- [ ] Worldborder-Setup
- [ ] Netherportal setzen
- [x] Lootchests generieren
- [x] Chests mit Loottable auffüllen

# Spielstart
**1. Lobby**  
In dieser Phase werden Spieler in einem Glaskasten weit über dem Füllhorn (in Sichtweite) gesammelt.  
Dort sind sie im Adventure-Modus, erhalten Saturation und können sich nicht schlagen. Diese Phase ist  
essenziell für die Team-Wahl. Haben alle Spieler einen Teampartner, kann die nächste Sequenz  
gestartet werden.

**2. Engaging Sequence**  
Spieler werden in die unterirdische Lobby teleportiert. Diese besteht aus einem großen, kreisförmigen  
Raum aus weißem Beton, in dem die Spieler unter einer Glasröhre spawnen. Diese Glasröhre gibt es in  
zwei Ausführungen: Eine für Ecken und eine für gerade Strecken.  
Anschließend erhalten die Spieler einen Levitation-Effekt und werden durch die Glasröhre nach oben  
schweben. ACHTUNG: Die Mitte der Start-Plattform muss automatisch entfernt werden, damit Spieler hindurch-  
fliegen können.  
Oben angekommen, wird unter den Spielern die Plattform wieder geschlossen. Sie stehen jetzt in einem Kreis  
um das Füllhorn herum. Sie können sich aufgrund von Barrier Blöcken, die um die Start-Plattform platziert sind,  
nicht bewegen. Sie sind außerdem im Adventure-Modus, solange bis das Spiel beginnt. Weiterhin sind alle  
Spieler weiterhin mit Saturation versehen.

**3. Game Start**  
Das Spiel kann manuell über /start gestartet werden. Per Title sollte ein Countdown gestartet werden.  
Nach Ablauf des Countdowns werden die Barrier-Blöcke um die Startplattform herum entfernt, und Spieler können sich nun frei bewegen.
Spieler werden außerdem in den Survival-Modus versetzt, für den Fall der Fälle noch einmal geheilt.  
Es startet außerdem die Grace Period. Spieler können nun in die Mitte laufen, um sich Loot zu holen.